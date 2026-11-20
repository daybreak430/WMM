package com.wmm.ledger.data

import org.junit.Assert.*
import org.junit.Test

class PaymentScreenParserTest {
    private val now = System.currentTimeMillis()
    private val base = listOf("支付成功", "¥18.50", "收款方", "测试便利店", "付款方式", "招商银行信用卡(1234)",
        "交易单号", "202611201234567890")

    @Test fun recognizesBothSourcesAndOnlyKeepsHashedOrderIdentifiers() {
        PaymentSource.entries.forEach { source ->
            val payment = PaymentScreenParser.parse(source, base, now)!!
            assertEquals(1850L, payment.cents)
            assertEquals("测试便利店", payment.merchant)
            assertEquals("招商银行信用卡(1234)", payment.method)
            assertEquals(64, payment.orderHash!!.length)
            assertFalse(payment.orderHash.contains("202611201234567890"))
            assertEquals(payment.fingerprint("first"), payment.fingerprint("second"))
        }
    }

    @Test fun actualPaidLabelWinsOverListPriceAndSeparatedCurrencyNodesWork() {
        val payment = PaymentScreenParser.parse(PaymentSource.WECHAT,
            listOf("付款成功", "原价", "￥20.00", "实付金额", "￥", "18.50", "收款方：测试便利店"), now)!!
        assertEquals(1850L, payment.cents)
        assertEquals(123450L, PaymentScreenParser.parse(PaymentSource.ALIPAY,
            listOf("支付成功", "￥１，２３４．５０", "商品说明", "测试商品"), now)!!.cents)
    }

    @Test fun ambiguousAmountsUnsupportedPagesAndMalformedMoneyNeverBecomeExpenses() {
        listOf("转账", "信用卡还款", "退款", "付款失败", "收款成功").forEach { marker ->
            assertNull(marker, PaymentScreenParser.parse(PaymentSource.WECHAT, base + marker, now))
        }
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, listOf("账单详情") + base, now))
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, listOf("返回", "交易详情") + base, now))
        assertNotNull(PaymentScreenParser.parse(PaymentSource.WECHAT, base + "账单详情", now))
        assertEquals(1850L, PaymentScreenParser.parse(PaymentSource.ALIPAY,
            listOf("支付成功", "18.50", "收款方", "验收便利店"), now)!!.cents)
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, base + "¥20.00", now))
        listOf("¥18.501", "18.501元", "¥1,23", "¥0.00", "¥-18.50").forEach { amount ->
            assertNull(amount, PaymentScreenParser.parse(PaymentSource.WECHAT, base.map { if (it == "¥18.50") amount else it }, now))
        }
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, listOf("支付成功", "¥18.50", "收款方", "付款方式", "零钱"), now))
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, base + "付款时间 2020-01-01 12:00", now))
        listOf("优惠金额", "账户余额", "原价").forEach { label ->
            assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT,
                listOf("支付成功", label, "¥2.50", "收款方", "验收便利店"), now))
        }
    }

    @Test fun missingOrderIdOnlyCoalescesTheSameVisiblePaymentSession() {
        val payment = PaymentScreenParser.parse(PaymentSource.WECHAT, base.take(6), now)!!
        assertEquals(payment.fingerprint("same"), payment.fingerprint("same"))
        assertNotEquals(payment.fingerprint("first"), payment.fingerprint("second"))
        val capture = PaymentCapture("id", "WECHAT", 1850, "测试便利店", "", now, "hash")
        val same = Entry("old", "EXPENSE", 1850, "meal", null, "测试便利店 · 午餐", now - 1000, "2026-11-20")
        assertEquals(listOf(same), capture.similarEntries(listOf(same)))
        assertTrue(capture.similarEntries(listOf(same.copy(note = "另一家店"))).isEmpty())
    }

    @Test fun detailsRequireOptInAndKeepOriginalTimeAndCrossScreenOrderIdentity() {
        val order = "202611201234567890"
        val wechat = listOf("返回", "账单详情", "测试便利店", "-18.50", "当前状态", "支付成功",
            "商品", "午餐", "商户全称", "测试便利店", "支付时间", "2024-02-29 13:14:15",
            "支付方式", "零钱", "交易单号", order)
        val alipay = listOf("账单详情", "测试便利店", "−￥18.50", "交易成功", "付款时间：2024年2月29日 13:14:15",
            "付款方式", "花呗", "支付宝交易号：$order", "申请退款")
        listOf(PaymentSource.WECHAT to wechat, PaymentSource.ALIPAY to alipay).forEach { (source, detail) ->
            assertNull(PaymentScreenParser.parse(source, detail, now))
            val capture = PaymentScreenParser.parse(source, detail, now, includeDetails = true)!!
            assertEquals(1850L, capture.cents)
            assertEquals("测试便利店", capture.merchant)
            assertEquals(java.time.LocalDate.of(2024, 2, 29), LedgerRepository.bookingDay(capture.paidAt!!))
            assertEquals(PaymentScreenParser.parse(source, base, now)!!.fingerprint("payment"), capture.fingerprint("detail"))
        }
    }

    @Test fun detailExcludesNonExpensesAndDoesNotGuessMissingTransactionData() {
        val detail = listOf("账单详情", "测试便利店", "-18.50", "交易成功", "付款时间", "2025-03-18 12:34:56")
        fun parse(lines: List<String>) = PaymentScreenParser.parse(PaymentSource.ALIPAY, lines, now, includeDetails = true)
        val payment = parse(detail)!!
        assertEquals(payment.fingerprint("first"), payment.fingerprint("second"))
        assertNotEquals(payment.fingerprint("first"), parse(detail.dropLast(1) + "2025-03-18 12:35:56")!!.fingerprint("first"))
        listOf(
            listOf("账单") + detail.drop(1), detail.dropLast(2), detail.dropLast(1) + "2025-03-18 12:34",
            detail.dropLast(1) + "2099-03-18 12:34:56", detail + "交易类型: 转账",
            detail + "当前状态: 交易关闭", detail.map { if (it == "-18.50") "+18.50" else it },
            detail.map { if (it == "测试便利店") "信用卡还款" else it },
            detail.map { if (it == "-18.50") "18.50" else it },
            detail.take(2) + listOf("优惠金额", "-2.50") + detail.drop(3),
            detail.take(3) + "-2.50" + detail.drop(3),
        ).forEach { assertNull(it.toString(), parse(it)) }
    }

    @Test fun alipayAutomaticDebitUsesTheNegativeRmbAmountAndPaymentTime() {
        val local = listOf("账单详情", "联系商家", "示例出行", "-10.60", "自动扣款成功", "管理免密支付",
            "示例出行免密支付", "支付时间", "2025-04-06 15:48:34", "付款方式", "储蓄卡(0000)",
            "商品说明", "快车行程", "乘车时间", "2025-04-06 15:25:50")
        val foreign = listOf("账单详情", "联系商家", "示例海外商户", "-486.02", "自动扣款成功", "管理自动续费",
            "付款授权", "订单金额", "486.02元(568.00港币)", "汇率", "1.00港币 = 0.85567000人民币",
            "支付时间", "2025-04-06 16:11:29", "付款方式", "储蓄卡(0000)", "收款方全称", "示例海外商户")
        fun parse(lines: List<String>) = PaymentScreenParser.parse(PaymentSource.ALIPAY, lines, now, includeDetails = true)
        val localPayment = parse(local)!!
        assertEquals(1060L, localPayment.cents)
        assertEquals("示例出行", localPayment.merchant)
        assertEquals(java.time.LocalTime.of(15, 48, 34), java.time.Instant.ofEpochMilli(localPayment.paidAt!!)
            .atZone(java.time.ZoneId.systemDefault()).toLocalTime())
        assertEquals(48602L, parse(foreign)!!.cents)
        assertNull(parse(local.map { if (it == "自动扣款成功") "自动扣款失败" else it }))
        assertNull(parse(local.map { if (it == "-10.60") "+10.60" else it }))
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, local, now, includeDetails = true))
        assertNull(PaymentScreenParser.parse(PaymentSource.ALIPAY, local, now))
    }

    @Test fun titlelessWechatQrReceiptAcceptsTransferFieldsOnlyForExplicitOutgoingQrPayment() {
        val qr = listOf("关闭", "扫二维码付款-给示例商户", "−4.00", "当前状态", "支付成功", "收款方备注", "二维码收款",
            "支付方式", "零钱", "转账时间", "2025年4月6日 08:22:28", "转账单号", "10000000000000123456789",
            "账单服务", "发起群收款", "申请电子凭证")
        fun parse(lines: List<String>) = PaymentScreenParser.parse(PaymentSource.WECHAT, lines, now, includeDetails = true)
        val payment = parse(qr)!!
        assertEquals(400L, payment.cents)
        assertEquals("示例商户", payment.merchant)
        assertEquals("零钱", payment.method)
        assertEquals(java.time.LocalDate.of(2025, 4, 6), LedgerRepository.bookingDay(payment.paidAt!!))
        assertEquals(64, payment.orderHash!!.length)
        assertEquals(payment.fingerprint("first"), parse(qr)!!.fingerprint("second"))
        assertNull(PaymentScreenParser.parse(PaymentSource.WECHAT, qr, now))
        assertNull(parse(qr.map { if (it.startsWith("扫二维码付款")) "转账-给示例商户" else it }))
        assertNull(parse(qr.map { if (it == "−4.00") "+4.00" else it }))
        assertNull(parse(qr + "退款成功"))
        assertNull(parse(qr.filterNot { it == "转账单号" }))
    }

    @Test fun alipayRecordingVariantsKeepTheirDirectionAndBookingDate() {
        val paidAt = "2025-11-09 17:44:57"
        fun parse(lines: List<String>) = PaymentScreenParser.parse(PaymentSource.ALIPAY, lines, now, includeDetails = true)
        val expenses = listOf(
            listOf("账单详情", "示例酒店", "-90.86", "交易成功", "支付时间", paidAt, "付款方式", "储蓄卡", "收款方全称", "示例酒店有限公司"),
            listOf("返回，账单详情", "示例软件服务", "−", "20.00", "交易成功", "支付时间$paidAt", "付款方式储蓄卡", "商品说明", "服务"),
            listOf("返回", "示例餐饮", "\u200e-10.00", "交易成功", "支付时间：$paidAt", "付款方式：储蓄卡", "商品说明", "餐饮"),
            listOf("账单详情", "示例视频服务", "-10.00", "自动扣款成功", "管理自动续费", "支付时间", paidAt, "付款方式", "储蓄卡"),
            listOf("账单详情", "示例外卖平台", "-51.40", "交易成功", "支付时间", paidAt, "商品说明", "餐饮订单", "服务详情", "13元优惠券"),
        )
        assertEquals(listOf(9086L, 2000L, 1000L, 1000L, 5140L), expenses.map { detail ->
            val payment = parse(detail)!!
            assertEquals(CaptureType.EXPENSE, payment.type)
            assertEquals(java.time.LocalDate.of(2025, 11, 9), LedgerRepository.bookingDay(payment.paidAt!!))
            payment.cents
        })
        val refund = listOf("账单详情", "示例出行", "16.50", "退款成功", "关联记录", "查看原账单", "创建时间", paidAt,
            "退款方式", "储蓄卡", "退款进度", "银行处理中", "银行处理成功", "商品说明", "退款-预付车费")
        assertEquals(CaptureType.REFUND, parse(refund)?.type)
        assertEquals(1650L, parse(refund)?.cents)
        assertNull(parse(refund.map { if (it == "退款成功") "退款处理中" else it }))
        for (account in listOf("示例银行", "示例信用购")) {
            val repayment = listOf("账单详情", account, "1,180.34", "还款成功", "创建时间", paidAt, "付款方式", "储蓄卡",
                "还款到", account, "处理进度", "银行处理中", "银行入账成功")
            assertEquals(CaptureType.REPAYMENT, parse(repayment)?.type)
            assertEquals(118034L, parse(repayment)?.cents)
            assertNull(parse(repayment.filterNot { it == "还款到" }))
        }
        assertNull(parse(listOf("搜索交易记录", "账单") + expenses[2]))
        assertNull(parse(listOf("返回", "账单详情")))
        assertEquals(CaptureType.EXPENSE, parse(expenses[0].map { if (it == "交易成功") "已全额退款" else it })?.type)
    }
}
