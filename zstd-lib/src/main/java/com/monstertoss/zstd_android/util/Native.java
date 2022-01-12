package com.monstertoss.zstd_android.util;

public class Native {
    private static boolean loaded = false;

    public static synchronized void load() {
        if (!loaded) {
            System.loadLibrary("zstd");
            System.loadLibrary("zstd-android");
            loaded = true;
        }
    }
}
