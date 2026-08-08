package com.letovpn.checker;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {

    public static final String NAME = "letovpn_prefs_v2";
    public static final String SYSTEM_SOURCE =
            "https://raw.githubusercontent.com/adop1344-bot/LetoVPN_free/refs/heads/main/sources.txt";

    private Prefs() {}

    public static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String getTheme(Context c) {
        return sp(c).getString("theme", ThemeHelper.THEME_DARK);
    }

    public static void setTheme(Context c, String v) {
        sp(c).edit().putString("theme", v).commit();
    }

    public static String getMethod(Context c) {
        return sp(c).getString("method", "TCP_DNS");
    }

    public static void setMethod(Context c, String v) {
        sp(c).edit().putString("method", v).commit();
    }

    public static int getCount(Context c) {
        return sp(c).getInt("count", 50);
    }

    public static void setCount(Context c, int v) {
        sp(c).edit().putInt("count", v).commit();
    }

    public static int getThreads(Context c) {
        return sp(c).getInt("threads", 12);
    }

    public static void setThreads(Context c, int v) {
        sp(c).edit().putInt("threads", v).commit();
    }

    public static Set<String> getSources(Context c) {
        Set<String> raw = sp(c).getStringSet("custom_sources", null);
        return raw == null ? new HashSet<>() : new HashSet<>(raw);
    }

    public static void setSources(Context c, Set<String> sources) {
        sp(c).edit().putStringSet("custom_sources", new HashSet<>(sources)).commit();
    }

    /** Отключённые источники (URL). Системный = Prefs.SYSTEM_SOURCE */
    public static Set<String> getDisabledSources(Context c) {
        Set<String> raw = sp(c).getStringSet("disabled_sources", null);
        return raw == null ? new HashSet<>() : new HashSet<>(raw);
    }

    public static void setDisabledSources(Context c, Set<String> set) {
        sp(c).edit().putStringSet("disabled_sources", new HashSet<>(set)).commit();
    }

    public static boolean isSourceEnabled(Context c, String url) {
        return !getDisabledSources(c).contains(url);
    }

    public static void setSourceEnabled(Context c, String url, boolean enabled) {
        Set<String> disabled = getDisabledSources(c);
        if (enabled) disabled.remove(url);
        else disabled.add(url);
        setDisabledSources(c, disabled);
    }

    /** Остановиться после N рабочих (0 = без лимита) */
    public static int getStopAfter(Context c) {
        return sp(c).getInt("stop_after", 0);
    }

    public static void setStopAfter(Context c, int v) {
        sp(c).edit().putInt("stop_after", v).commit();
    }

    public static boolean getOnlyVless(Context c) {
        return sp(c).getBoolean("only_vless", false);
    }

    public static void setOnlyVless(Context c, boolean v) {
        sp(c).edit().putBoolean("only_vless", v).commit();
    }
}
