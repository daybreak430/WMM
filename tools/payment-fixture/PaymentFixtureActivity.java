package com.wmm.capturefixture;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

// 仅用于无真实支付应用的隔离模拟器，不联网、不执行付款。
public class PaymentFixtureActivity extends Activity {
    private LinearLayout content;
    private int refresh = 0;
    @Override public void onCreate(Bundle saved) { super.onCreate(saved); home(); }
    private void reset() {
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 100, 32, 40);
        setContentView(content);
    }
    private void text(String value) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(20);
        label.setPadding(8, 8, 8, 8);
        content.addView(label);
    }
    private void button(String title, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(view -> action.run());
        content.addView(button);
    }
    private void home() {
        reset(); text("隔离模拟器付款测试");
        button("付款 A", () -> payment("支付成功", "20261120000001", false));
        button("付款 B（另一笔同额消费）", () -> payment("支付成功", "20261120000002", false));
        button("付款失败", () -> payment("付款失败", "20261120000003", false));
        button("转账测试", () -> payment("转账成功", "20261120000004", false));
        button("多金额测试", () -> payment("支付成功", "20261120000005", true));
        button("历史消费详情", this::details);
        button("录屏布局测试", this::recordingLayout);
        if (getPackageName().contains("Alipay")) {
            button("加载中的普通消费", this::delayedPurchase);
            button("退款详情", this::refund);
            button("还款详情", this::repayment);
        }
    }
    private void payment(String status, String order, boolean ambiguous) {
        reset(); text(status); text("￥18.50");
        if (ambiguous) text("￥20.00");
        text("收款方"); text("验收便利店");
        text("付款方式"); text(getPackageName().contains("Alipay") ? "花呗" : "信用卡(1234)");
        text("交易单号"); text(order);
        button("重复刷新", () -> content.setContentDescription("测试刷新 " + (++refresh)));
        button("返回测试首页", this::home);
    }
    private void details() {
        boolean alipay = getPackageName().contains("Alipay");
        reset(); text("账单详情"); text("验收便利店"); text("-18.50");
        if (!alipay) text("当前状态");
        text(alipay ? "交易成功" : "支付成功");
        text(alipay ? "付款时间" : "支付时间"); text("2025-04-06 12:34:56");
        text(alipay ? "付款方式" : "支付方式"); text(alipay ? "花呗" : "零钱");
        text(alipay ? "支付宝交易号" : "交易单号"); text("20250406000001");
        button("返回测试首页", this::home);
    }

    private void recordingLayout() {
        boolean alipay = getPackageName().contains("Alipay");
        reset();
        if (alipay) {
            text("账单详情"); text("联系商家"); text("模拟海外商户"); text("-486.02"); text("自动扣款成功");
            text("管理自动续费"); text("付款授权"); text("订单金额"); text("486.02元(568.00港币)");
            text("支付时间"); text("2025-04-06 16:11:29"); text("付款方式"); text("储蓄卡(0000)");
        } else {
            text("扫二维码付款-给模拟商户"); text("−4.00"); text("当前状态"); text("支付成功");
            text("收款方备注"); text("二维码收款"); text("支付方式"); text("零钱");
            text("转账时间"); text("2025年4月6日 08:22:28"); text("转账单号"); text("10000000000000123456789");
        }
        button("返回测试首页", this::home);
    }

    private void delayedPurchase() {
        reset(); text("返回，账单详情"); text("加载中");
        Handler handler = new Handler(Looper.getMainLooper());
        for (int index = 1; index <= 36; index++) {
            int frame = index;
            handler.postDelayed(() -> content.setContentDescription("更新 " + frame), index * 80L);
        }
        handler.postDelayed(() -> {
            reset(); text("返回，账单详情"); text("模拟餐饮"); text("−"); text("12.00"); text("交易成功");
            text("支付时间2025-04-06 12:34:56"); text("付款方式储蓄卡"); text("商品说明"); text("模拟餐饮订单");
            button("更多", () -> { text("订单号"); text("20250406000009"); });
            button("返回测试首页", this::home);
        }, 1550);
    }

    private void refund() {
        reset(); text("账单详情"); text("模拟餐饮"); text("6.00"); text("退款成功");
        text("关联记录"); text("查看原账单"); text("创建时间"); text("2025-04-07 12:34:56");
        text("退款方式"); text("储蓄卡"); text("退款进度"); text("银行处理中"); text("银行处理成功");
        button("返回测试首页", this::home);
    }

    private void repayment() {
        reset(); text("账单详情"); text("示例信用账户"); text("180.00"); text("还款成功");
        text("创建时间"); text("2025-04-08 12:34:56"); text("付款方式"); text("储蓄卡");
        text("还款到"); text("示例信用账户"); text("处理进度"); text("银行入账成功");
        button("返回测试首页", this::home);
    }
}
