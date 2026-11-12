package com.btb.wallhook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public class ModernHook extends XposedModule {

    private static final String TARGET_PACKAGE = "com.miui.gallery";
    private static final String HANDLER_PACKAGE = "com.btb.wallpaperfix";
    private static final String HANDLER_CLASS = "com.btb.wallpaperfix.MainActivity";
    private static String logPath = "";

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        logPath = param.getApplicationInfo().dataDir + "/files/wallhook.log";
        logLine("module loaded in " + param.getPackageName());

        try {
            Method startActivity = Activity.class.getDeclaredMethod("startActivity", Intent.class);
            hook(startActivity).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    rewrite((Intent) chain.getArg(0), "startActivity");
                    return chain.proceed();
                }
            });

            Method startActivityOptions = Activity.class.getDeclaredMethod("startActivity", Intent.class, Bundle.class);
            hook(startActivityOptions).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    rewrite((Intent) chain.getArg(0), "startActivity+options");
                    return chain.proceed();
                }
            });

            Method forResult = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class);
            hook(forResult).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    rewrite((Intent) chain.getArg(0), "startActivityForResult");
                    return chain.proceed();
                }
            });

            Method forResultOptions = Activity.class.getDeclaredMethod("startActivityForResult", Intent.class, int.class, Bundle.class);
            hook(forResultOptions).intercept(new XposedInterface.Hooker() {
                @Override
                public Object intercept(XposedInterface.Chain chain) throws Throwable {
                    rewrite((Intent) chain.getArg(0), "startActivityForResult+options");
                    return chain.proceed();
                }
            });

            logLine("hooks installed");
        } catch (Throwable throwable) {
            logLine("hook failed: " + throwable);
        }
    }

    private static void rewrite(Intent intent, String source) {
        if (intent == null) return;
        String action = String.valueOf(intent.getAction());
        ComponentName component = intent.getComponent();
        String componentText = component == null ? "" : component.flattenToShortString();
        String lowerAction = action.toLowerCase();
        String lowerComponent = componentText.toLowerCase();

        boolean candidate = lowerAction.contains("wallpaper") || lowerComponent.contains("wallpaper")
                || "com.android.thememanager".equals(component == null ? "" : component.getPackageName())
                || "com.miui.miwallpaper".equals(component == null ? "" : component.getPackageName());
        if (!candidate) return;

        logLine("hit " + source + " action=" + action + " comp=" + componentText
                + " data=" + intent.getData() + " extras=" + intent.getExtras());

        intent.setSelector(null);
        intent.setComponent(new ComponentName(HANDLER_PACKAGE, HANDLER_CLASS));
        intent.setAction("miui.intent.action.START_WALLPAPER_DETAIL");
        logLine("redirected -> " + HANDLER_PACKAGE);
    }

    private static void logLine(String message) {
        String line = new SimpleDateFormat("MM-dd HH:mm:ss").format(new Date()) + " " + message;
        try {
            if (!logPath.isEmpty()) {
                File file = new File(logPath);
                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                FileWriter writer = new FileWriter(file, true);
                writer.write(line);
                writer.write("\n");
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}