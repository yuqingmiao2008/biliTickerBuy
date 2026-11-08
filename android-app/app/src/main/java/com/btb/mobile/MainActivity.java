package com.btb.mobile;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private static final String SERVER_URL = "http://127.0.0.1:7860/";
    private static final String TERMUX_PREFIX = "/data/data/com.termux/files/usr";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";

    private WebView webView;
    private LinearLayout statusPanel;
    private TextView statusText;
    private ProgressBar progressBar;
    private Button retryButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        statusPanel = new LinearLayout(this);
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setGravity(Gravity.CENTER);
        statusPanel.setPadding(48, 48, 48, 48);
        statusPanel.setBackgroundColor(0xF0121212);

        progressBar = new ProgressBar(this);
        statusText = new TextView(this);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setText("正在启动本地服务…\n首次启动需要几十秒");
        retryButton = new Button(this);
        retryButton.setText("重试");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> startBootstrap());

        statusPanel.addView(progressBar);
        statusPanel.addView(statusText);
        statusPanel.addView(retryButton);
        root.addView(statusPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        startBootstrap();
    }

    private void startBootstrap() {
        if (started) return;
        started = true;
        retryButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setText("正在启动本地服务…\n首次启动需要几十秒");

        new Thread(() -> {
            if (isServerUp()) {
                showWebView();
                return;
            }
            String rootResult = runSu("id");
            if (rootResult == null || !rootResult.contains("uid=0")) {
                showError("没有拿到 root 权限。\n本机需要 root + Termux 环境。");
                return;
            }
            runSu(buildStartCommand());
            for (int i = 0; i < 90; i++) {
                if (isServerUp()) {
                    showWebView();
                    return;
                }
                sleep(1000);
                final int seconds = i + 1;
                handler.post(() -> statusText.setText("正在启动本地服务… " + seconds + "s"));
            }
            showError("启动超时。\n请检查 Termux/proot 环境是否完整，或查看 ~/btb-server.log。");
        }).start();
    }

    private String buildStartCommand() {
        String script =
                "export PREFIX=" + TERMUX_PREFIX + ";" +
                "export HOME=" + TERMUX_HOME + ";" +
                "export PATH=$PREFIX/bin:$PATH;" +
                "cd $HOME/biliTickerBuy || exit 1;" +
                "nohup proot-distro login debian --bind $HOME/biliTickerBuy:/opt/btb -- " +
                "bash -lc 'cd /opt/btb && exec .venv/bin/python main.py ui --server_name 127.0.0.1 --port 7860' " +
                "> $HOME/btb-server.log 2>&1 &";
        return script;
    }

    private String runSu(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
            process.waitFor();
            return out.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isServerUp() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SERVER_URL).openConnection();
            connection.setConnectTimeout(1500);
            connection.setReadTimeout(1500);
            connection.setRequestMethod("GET");
            int code = connection.getResponseCode();
            return code >= 200 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void showWebView() {
        handler.post(() -> {
            statusPanel.setVisibility(View.GONE);
            webView.loadUrl(SERVER_URL);
        });
    }

    private void showError(String message) {
        handler.post(() -> {
            progressBar.setVisibility(View.GONE);
            statusText.setText(message);
            retryButton.setVisibility(View.VISIBLE);
            started = false;
        });
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
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