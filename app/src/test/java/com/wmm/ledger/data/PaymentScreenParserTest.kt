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
}
