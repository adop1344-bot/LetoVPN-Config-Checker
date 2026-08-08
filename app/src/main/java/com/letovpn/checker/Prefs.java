package com.letovpn.checker;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

/** Надёжное хранение настроек (фикс бага StringSet в Android). */
public final class Prefs {

    public static final String NAME = "letovpn_prefs_v2";

    public static final String KEY_THEME = "theme";
    public static final String KEY_METHOD = "method";
    public static final String KEY_COUNT = "count";
    public static final String KEY_THREADS = "threads";
    public static final String KEY_SOURCES = "custom_sources";

    private Prefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context c) {
        return sp(c).getString(KEY_THEME, ThemeHelper.THEME_DARK);
    }

    public static void setTheme(Context c, String v) {
        sp(c).edit().putString(KEY_THEME, v).commit();
    }

    public static String getMethod(Context c) {
        return sp(c).getString(KEY_METHOD, "TCP_DNS");
    }

    public static void setMethod(Context c, String v) {
        sp(c).edit().putString(KEY_METHOD, v).commit();
    }

    public static int getCount(Context c) {
        return sp(c).getInt(KEY_COUNT, 50);
    }

    public static void setCount(Context c, int v) {
        sp(c).edit().putInt(KEY_COUNT, v).commit();
    }

    public static int getThreads(Context c) {
        return sp(c).getInt(KEY_THREADS, 12);
    }

    public static void setThreads(Context c, int v) {
        sp(c).edit().putInt(KEY_THREADS, v).commit();
    }

    public static Set<String> getSources(Context c) {
        Set<String> raw = sp(c).getStringSet(KEY_SOURCES, null);
        if (raw == null) return new HashSet<>();
        return new HashSet<>(raw); // обязательно копия!
    }

    public static void setSources(Context c, Set<String> sources) {
        sp(c).edit().putStringSet(KEY_SOURCES, new HashSet<>(sources)).commit();
    }
}
