package com.letovpn.checker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private MaterialButton btnStart, btnSave, btnCopyTop;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView recyclerView;
    private ConfigAdapter adapter;

    private ExecutorService executor;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning = false;

    private final List<ConfigItem> workingConfigs = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeHelper.apply(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btnStart);
        btnSave = findViewById(R.id.btnSave);
        btnCopyTop = findViewById(R.id.btnCopyTop);
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        recyclerView = findViewById(R.id.recyclerView);

        adapter = new ConfigAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        LayoutAnimationController anim =
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
        recyclerView.setLayoutAnimation(anim);

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        btnStart.setOnClickListener(v -> {
            if (isRunning) stopCheck();
            else startCheck();
        });

        btnSave.setOnClickListener(v -> showExportMenu());
        btnCopyTop.setOnClickListener(v -> copyTop10());

        // long press copy top = export sub
        btnCopyTop.setOnLongClickListener(v -> {
            exportSubscription();
            return true;
        });
    }

    private void startCheck() {
        isRunning = true;
        workingConfigs.clear();
        adapter.clear();
        btnStart.setText("⏹ Стоп");
        btnSave.setEnabled(false);
        btnCopyTop.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText("Загрузка источников...");

        int threads = Prefs.getThreads(this);
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();

        String methodStr = Prefs.getMethod(this);
        boolean isXray = "XRAY".equals(methodStr) || "XRAY_SPEED".equals(methodStr);
        int poolSize = isXray
                ? Math.min(3, Math.max(1, threads))
                : Math.max(2, Math.min(100, threads));
        executor = Executors.newFixedThreadPool(poolSize);

        final Context appCtx = getApplicationContext();
        final int stopAfter = Prefs.getStopAfter(this);
        final boolean onlyVless = Prefs.getOnlyVless(this);

        executor.execute(() -> {
            try {
                if (isXray) {
                    mainHandler.post(() -> statusText.setText("Скачивание Xray-core..."));
                    if (!XrayEngine.ensureBinary(appCtx)) {
                        mainHandler.post(() -> {
                            statusText.setText("Не удалось скачать Xray");
                            finishCheck();
                        });
                        return;
                    }
                }

                // источники с учётом вкл/выкл
                List<String> metaSources = new ArrayList<>();
                if (Prefs.isSourceEnabled(appCtx, Prefs.SYSTEM_SOURCE)) {
                    metaSources.add(Prefs.SYSTEM_SOURCE);
                }
                for (String s : Prefs.getSources(appCtx)) {
                    if (Prefs.isSourceEnabled(appCtx, s)) metaSources.add(s);
                }

                if (metaSources.isEmpty()) {
                    mainHandler.post(() -> {
                        statusText.setText("Все источники выключены");
                        finishCheck();
                    });
                    return;
                }

                // раскрываем sources.txt → реальные URL списков
                List<String> listUrls = new ArrayList<>();
                for (String src : metaSources) {
                    if (!isRunning) break;
                    if (src.equals(Prefs.SYSTEM_SOURCE)) {
                        try {
                            for (String line : fetchLines(src)) {
                                line = line.trim();
                                if (line.startsWith("http")) listUrls.add(line);
                            }
                        } catch (Exception ignored) {}
                    } else {
                        listUrls.add(src);
                    }
                }

                List<String> allConfigs = new ArrayList<>();
                for (String src : listUrls) {
                    if (!isRunning) break;
                    try {
                        for (String line : fetchLines(src.trim())) {
                            line = line.trim();
                            if (onlyVless) {
                                if (line.startsWith("vless://")) allConfigs.add(line);
                            } else if (line.startsWith("vless://") || line.startsWith("vmess://")
                                    || line.startsWith("trojan://")) {
                                allConfigs.add(line);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(allConfigs));

                int maxCount = Prefs.getCount(appCtx);
                ConfigChecker.Method method;
                try {
                    method = ConfigChecker.Method.valueOf(methodStr);
                } catch (Exception e) {
                    method = ConfigChecker.Method.TCP_DNS;
                }

                if (maxCount > 0 && unique.size() > maxCount) {
                    unique = unique.subList(0, maxCount);
                }

                final int total = unique.size();
                final String methodLabel = ConfigChecker.methodName(method);
                mainHandler.post(() -> {
                    statusText.setText("Найдено: " + total + " · " + methodLabel);
                    progressBar.setMax(Math.max(1, total));
                });

                if (total == 0) {
                    mainHandler.post(this::finishCheck);
                    return;
                }

                AtomicInteger done = new AtomicInteger(0);
                final ConfigChecker.Method m = method;

                for (String raw : unique) {
                    if (!isRunning) break;

                    executor.execute(() -> {
                        if (!isRunning) return;

                        ConfigItem item = new ConfigItem(raw);
                        long latency = ConfigChecker.test(item, m, appCtx);
                        item.latency = latency;
                        item.working = latency > 0;

                        int current = done.incrementAndGet();

                        mainHandler.post(() -> {
                            if (item.working) {
                                workingConfigs.add(item);
                                Collections.sort(workingConfigs,
                                        (a, b) -> Long.compare(a.latency, b.latency));
                                adapter.setItems(new ArrayList<>(workingConfigs));
                                recyclerView.scheduleLayoutAnimation();

                                if (stopAfter > 0 && workingConfigs.size() >= stopAfter) {
                                    isRunning = false;
                                    statusText.setText("Стоп: набрано " + stopAfter + " рабочих");
                                    finishCheck();
                                    return;
                                }
                            }
                            progressBar.setProgress(current);
                            statusText.setText("Проверено: " + current + "/" + total
                                    + " · рабочих: " + workingConfigs.size());

                            if (current >= total || !isRunning) finishCheck();
                        });
                    });
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Ошибка: " + e.getMessage());
                    finishCheck();
                });
            }
        });
    }

    private void stopCheck() {
        isRunning = false;
        finishCheck();
    }

    private void finishCheck() {
        isRunning = false;
        btnStart.setText("▶  Проверить");
        progressBar.setVisibility(View.GONE);
        boolean has = !workingConfigs.isEmpty();
        btnSave.setEnabled(has);
        btnCopyTop.setEnabled(has);
        if (has && !statusText.getText().toString().startsWith("Стоп")) {
            statusText.setText("Готово · рабочих: " + workingConfigs.size());
        } else if (!has) {
            String t = statusText.getText().toString();
            if (t.startsWith("Проверено") || t.startsWith("Найдено")) {
                statusText.setText("Готово · рабочих нет");
            }
        }
    }

    private List<String> fetchLines(String urlStr) throws Exception {
        List<String> lines = new ArrayList<>();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("User-Agent", "LetoVPN-Checker/1.6");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        }
        return lines;
    }

    private void showExportMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Сохранить")
                .setItems(new String[]{"TXT файл", "Подписка (base64) в буфер", "Топ-10 в буфер"}, (d, which) -> {
                    if (which == 0) saveAllToFile();
                    else if (which == 1) exportSubscription();
                    else copyTop10();
                })
                .show();
    }

    private void saveAllToFile() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, "letovpn_working_configs.txt");
            try (FileWriter writer = new FileWriter(file)) {
                for (ConfigItem item : workingConfigs) writer.write(item.raw + "\n");
            }
            Toast.makeText(this, "Сохранено: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void exportSubscription() {
        if (workingConfigs.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (ConfigItem item : workingConfigs) sb.append(item.raw).append("\n");
        String b64 = Base64.encodeToString(
                sb.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("sub", b64));
        Toast.makeText(this, "Подписка (base64) скопирована", Toast.LENGTH_SHORT).show();
    }

    private void copyTop10() {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(10, workingConfigs.size());
        for (int i = 0; i < count; i++) sb.append(workingConfigs.get(i).raw).append("\n");
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("top10", sb.toString()));
        Toast.makeText(this, "Скопировано топ-" + count, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        if (executor != null) executor.shutdownNow();
    }
}
