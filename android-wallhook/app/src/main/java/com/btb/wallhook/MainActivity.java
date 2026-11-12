package com.btb.wallhook;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(40, 60, 40, 40);

        TextView text = new TextView(this);
        text.setText("壁纸重定向模块（LSPosed）\n\n"
                + "1. 在 LSPosed 管理器中启用本模块\n"
                + "2. 作用域勾选「相册」(com.miui.gallery)\n"
                + "3. 停止并重新打开小米相册\n"
                + "4. 选图 → 设为壁纸，会跳到「锁屏壁纸修复」页面\n\n"
                + "日志：/sdcard/Download/wallhook.log");
        text.setTextSize(16);
        text.setTextColor(Color.BLACK);
        text.setGravity(Gravity.CENTER);
        root.addView(text);

        setContentView(root);
    }
}