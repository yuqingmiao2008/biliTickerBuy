package com.btb.wallhook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.miui.gallery";
    private static final String HANDLER_PACKAGE = "com.btb.wallpaperfix";
    private static final String HANDLER_CLASS = "com.btb.wallpaperfix.MainActivity";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        log("module loaded in " + lpparam.packageName);
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    rewrite((Intent) param.args[0], "startActivity");
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "startActivity", Intent.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    rewrite((Intent) param.args[0], "startActivity+options");
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    rewrite((Intent) param.args[0], "startActivityForResult");
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "startActivityForResult", Intent.class, int.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    rewrite((Intent) param.args[0], "startActivityForResult+options");
                }
            });
        } catch (Throwable throwable) {
            log("hook failed: " + throwable);
        }
    }

    private static void rewrite(Intent intent, String source) {
        if (intent == null) {
            return;
        }
        String action = String.valueOf(intent.getAction());
        ComponentName component = intent.getComponent();
        String componentText = component == null ? "" : component.flattenToShortString();
        String lowerComponent = componentText.toLowerCase();
        String lowerAction = action.toLowerCase();

        boolean candidate = lowerAction.contains("wallpaper") || lowerComponent.contains("wallpaper")
                || "com.android.thememanager".equals(component == null ? "" : component.getPackageName())
                || "com.miui.miwallpaper".equals(component == null ? "" : component.getPackageName());
        if (!candidate) {
            return;
        }

        log("hit " + source + " action=" + action + " comp=" + componentText
                + " data=" + intent.getData() + " extras=" + intent.getExtras());

        intent.setSelector(null);
        intent.setComponent(new ComponentName(HANDLER_PACKAGE, HANDLER_CLASS));
        intent.setAction("miui.intent.action.START_WALLPAPER_DETAIL");
        log("redirected -> " + HANDLER_PACKAGE);
    }

    private static void log(String message) {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date()) + " " + message;
        XposedBridge.log("WallHook: " + message);
        try {
            File file = new File(Environment.getExternalStorageDirectory(), "Download/wallhook.log");
            FileWriter writer = new FileWriter(file, true);
            writer.write(line);
            writer.write("\n");
            writer.close();
        } catch (Throwable ignored) {
        }
    }
}