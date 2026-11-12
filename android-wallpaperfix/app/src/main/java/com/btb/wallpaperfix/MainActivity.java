package com.btb.wallpaperfix;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;

public class MainActivity extends Activity {

    private static final int REQ_PICK = 1001;

    private TextView statusText;
    private int pendingFlags = WallpaperManager.FLAG_LOCK;
    private Uri incomingUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(40, 60, 40, 40);

        TextView title = new TextView(this);
        title.setText("锁屏壁纸设置（标准 API）");
        title.setTextSize(20);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusText = new TextView(this);
        statusText.setText("选择一张图片，直接写入锁屏壁纸");
        statusText.setTextSize(15);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 24, 0, 24);
        root.addView(statusText);

        Button lockButton = new Button(this);
        lockButton.setText("设为锁屏壁纸");
        lockButton.setOnClickListener(v -> chooseOrApply(WallpaperManager.FLAG_LOCK));
        root.addView(lockButton);

        Button bothButton = new Button(this);
        bothButton.setText("设为桌面 + 锁屏");
        bothButton.setOnClickListener(v -> chooseOrApply(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK));
        root.addView(bothButton);

        Button pickButton = new Button(this);
        pickButton.setText("重新选择图片");
        pickButton.setOnClickListener(v -> {
            incomingUri = null;
            pick(WallpaperManager.FLAG_LOCK);
        });
        root.addView(pickButton);

        setContentView(root);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        Uri uri = intent.getData();
        if (uri == null) uri = parseUri(intent.getStringExtra("Wallpaper_uri"));
        if (uri == null) uri = parseUri(intent.getStringExtra("wallpaper_uri"));
        if (uri == null) uri = parseUri(intent.getStringExtra("image_uri"));
        if (uri == null && intent.getParcelableExtra(Intent.EXTRA_STREAM) instanceof Uri) {
            uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        }
        if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            uri = intent.getClipData().getItemAt(0).getUri();
        }
        if (uri != null) {
            incomingUri = uri;
            statusText.setText("已收到图片，点下面按钮设置范围");
        }
    }

    private Uri parseUri(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Uri.parse(value);
        } catch (Exception exc) {
            return null;
        }
    }

    private void chooseOrApply(int flags) {
        if (incomingUri != null) {
            applyUri(incomingUri, flags);
        } else {
            pick(flags);
        }
    }

    private void pick(int flags) {
        pendingFlags = flags;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQ_PICK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri != null) {
            incomingUri = uri;
            applyUri(uri, pendingFlags);
        }
    }

    private void applyUri(Uri uri, int flags) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                statusText.setText("图片解码失败");
                return;
            }
            WallpaperManager manager = WallpaperManager.getInstance(this);
            manager.setBitmap(bitmap, null, true, flags);
            String where = flags == WallpaperManager.FLAG_LOCK ? "锁屏" : "桌面 + 锁屏";
            String message = "已写入" + where + "壁纸：" + bitmap.getWidth() + "x" + bitmap.getHeight();
            statusText.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception exc) {
            statusText.setText("失败：" + exc);
            Toast.makeText(this, "失败：" + exc, Toast.LENGTH_LONG).show();
        }
    }
}