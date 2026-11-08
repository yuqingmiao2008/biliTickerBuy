package com.btb.standalone;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setTextSize(14);
        text.setPadding(40, 80, 40, 40);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);

        new Thread(() -> {
            String result;
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(this));
                }
                PyObject probe = Python.getInstance().getModule("probe");
                result = probe.callAttr("check").toString();
            } catch (Throwable t) {
                result = "Python 启动失败:\n" + android.util.Log.getStackTraceString(t);
            }
            final String out = result;
            runOnUiThread(() -> text.setText(out));
        }).start();
    }
}