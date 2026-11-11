package com.btb.standalone;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends Activity {

    private static final int PORT = 7861;
    private static final String URL = "http://127.0.0.1:" + PORT + "/";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private LinearLayout statusPanel;
    private TextView statusText;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        statusPanel.setBackgroundColor(0xF0121212);
        ProgressBar bar = new ProgressBar(this);
        statusText = new TextView(this);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("正在启动内置 Python…");
        statusPanel.addView(bar);
        statusPanel.addView(statusText);
        root.addView(statusPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "btb:run");
        wakeLock.acquire();

        new Thread(() -> {
            try {
                if (!Python.isStarted()) {
                    Python.start(new AndroidPlatform(this));
                }
                PyObject server = Python.getInstance().getModule("server");
                server.callAttr("start", getFilesDir().getAbsolutePath(), PORT);
                for (int i = 0; i < 60; i++) {
                    if (isUp()) {
                        handler.post(() -> {
                            statusPanel.setVisibility(View.GONE);
                            webView.loadUrl(URL);
                        });
                        return;
                    }
                    Thread.sleep(500);
                }
                showError("本地服务启动超时");
            } catch (Throwable t) {
                showError("启动失败:\n" + android.util.Log.getStackTraceString(t));
            }
        }).start();
    }

    private boolean isUp() {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                    new java.net.URL(URL).openConnection();
            c.setConnectTimeout(1200);
            c.setReadTimeout(1200);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private void showError(String message) {
        handler.post(() -> {
            statusText.setText(message);
            statusPanel.setVisibility(View.VISIBLE);
        });
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}