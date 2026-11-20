package com.wmm.capturefixture;

import android.app.Activity;
import android.os.Bundle;
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
}
