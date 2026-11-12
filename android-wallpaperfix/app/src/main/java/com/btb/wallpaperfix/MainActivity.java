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
        lockButton.setText("选择图片 → 仅设为锁屏");
        lockButton.setOnClickListener(v -> pick(WallpaperManager.FLAG_LOCK));
        root.addView(lockButton);

        Button bothButton = new Button(this);
        bothButton.setText("选择图片 → 桌面 + 锁屏");
        bothButton.setOnClickListener(v -> pick(WallpaperManager.FLAG_SYSTEM | WallpaperManager.FLAG_LOCK));
        root.addView(bothButton);

        setContentView(root);
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
        if (uri == null) {
            return;
        }
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(input);
            if (bitmap == null) {
                statusText.setText("图片解码失败");
                return;
            }
            WallpaperManager manager = WallpaperManager.getInstance(this);
            manager.setBitmap(bitmap, null, true, pendingFlags);
            String where = pendingFlags == WallpaperManager.FLAG_LOCK ? "锁屏" : "桌面 + 锁屏";
            String message = "已写入" + where + "壁纸：" + bitmap.getWidth() + "x" + bitmap.getHeight();
            statusText.setText(message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        } catch (Exception exc) {
            statusText.setText("失败：" + exc);
            Toast.makeText(this, "失败：" + exc, Toast.LENGTH_LONG).show();
        }
    }
}