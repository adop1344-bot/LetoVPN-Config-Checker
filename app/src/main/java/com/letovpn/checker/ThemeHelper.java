package com.letovpn.checker;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public class ThemeHelper {

    public static final String KEY_THEME = "theme";
    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_CUSTOM = "custom";
    public static final String THEME_DYNAMIC = "dynamic";

    public static void apply(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE);
        String theme = prefs.getString(KEY_THEME, THEME_DARK);

        switch (theme) {
            case THEME_LIGHT:
                activity.setTheme(R.style.Theme_LetoVPN_Light);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_CUSTOM:
                activity.setTheme(R.style.Theme_LetoVPN_Custom);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case THEME_DYNAMIC:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity.setTheme(R.style.Theme_LetoVPN_Dynamic);
                    DynamicColors.applyToActivityIfAvailable(activity);
                } else {
                    activity.setTheme(R.style.Theme_LetoVPN);
                }
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            default:
                activity.setTheme(R.style.Theme_LetoVPN);
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }
}
