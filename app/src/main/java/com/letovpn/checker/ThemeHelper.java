package com.letovpn.checker;

import android.app.Activity;
import android.os.Build;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.color.DynamicColors;

public class ThemeHelper {

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_CUSTOM = "custom";
    public static final String THEME_DYNAMIC = "dynamic";

    /** Вызывать ДО super.onCreate() */
    public static void apply(Activity activity) {
        String theme = Prefs.getTheme(activity);

        switch (theme) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                activity.setTheme(R.style.Theme_LetoVPN_Light);
                break;
            case THEME_CUSTOM:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                activity.setTheme(R.style.Theme_LetoVPN_Custom);
                break;
            case THEME_DYNAMIC:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                activity.setTheme(R.style.Theme_LetoVPN_Dynamic);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    DynamicColors.applyToActivityIfAvailable(activity);
                }
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                activity.setTheme(R.style.Theme_LetoVPN);
                break;
        }
    }
}
