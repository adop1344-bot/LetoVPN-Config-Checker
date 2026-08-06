package com.letovpn.checker;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends AppCompatActivity {

    private static final String SOURCES_URL = "https://raw.githubusercontent.com/adop1344-bot/LetoVPN_free/refs/heads/main/sources.txt";

    private MaterialButton btnStart, btnSave, btnCopyTop;
    private ProgressBar progressBar;
    private TextView statusText;
    private RecyclerView recyclerView;
    private ConfigAdapter adapter;

    private final ExecutorService executor = Executors.newFixedThreadPool(12);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean isRunning = false;

    private final List<ConfigItem> workingConfigs = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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

        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        btnStart.setOnClickListener(v -> {
            if (isRunning) {
                stopCheck();
            } else {
                startCheck();
            }
        });

        btnSave.setOnClickListener(v -> saveAllToFile());
        btnCopyTop.setOnClickListener(v -> copyTop10());
    }

    private void startCheck() {
        isRunning = true;
        workingConfigs.clear();
        adapter.clear();
        btnStart.setText(R.string.stop);
        btnSave.setEnabled(false);
        btnCopyTop.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        statusText.setText("Загрузка источников...");

        executor.execute(() -> {
            try {
                List<String> sources = fetchLines(SOURCES_URL);
                List<String> allConfigs = new ArrayList<>();

                for (String src : sources) {
                    if (!isRunning) break;
                    try {
                        List<String> lines = fetchLines(src.trim());
                        for (String line : lines) {
                            line = line.trim();
                            if (line.startsWith("vless://") || line.startsWith("vmess://") || line.startsWith("trojan://")) {
                                allConfigs.add(line);
                            }
                        }
                    } catch (Exception ignored) {}
                }

                // unique
                List<String> unique = new ArrayList<>(new java.util.LinkedHashSet<>(allConfigs));

                SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
                int maxCount = prefs.getInt(SettingsActivity.KEY_COUNT, 50);
                String modeStr = prefs.getString(SettingsActivity.KEY_MODE, "TCP");
                ConfigChecker.Mode mode = "PROXY_GET".equals(modeStr) ? ConfigChecker.Mode.PROXY_GET : ConfigChecker.Mode.TCP;

                if (unique.size() > maxCount) {
                    unique = unique.subList(0, maxCount);
                }

                final int total = unique.size();
                mainHandler.post(() -> {
                    statusText.setText("Найдено: " + total + " | Режим: " + modeStr);
                    progressBar.setMax(total);
                });

                AtomicInteger done = new AtomicInteger(0);

                for (String raw : unique) {
                    if (!isRunning) break;

                    executor.execute(() -> {
                        ConfigItem item = new ConfigItem(raw);
                        long latency = ConfigChecker.test(item, mode);
                        item.latency = latency;
                        item.working = latency > 0;

                        int current = done.incrementAndGet();

                        mainHandler.post(() -> {
                            if (item.working) {
                                workingConfigs.add(item);
                                // sort live by latency
                                Collections.sort(workingConfigs, (a, b) -> Long.compare(a.latency, b.latency));
                                adapter.setItems(new ArrayList<>(workingConfigs));
                            }
                            progressBar.setProgress(current);
                            statusText.setText("Проверено: " + current + "/" + total + " | Рабочих: " + workingConfigs.size());

                            if (current >= total || !isRunning) {
                                finishCheck();
                            }
                        });
                    });
                }

                if (total == 0) {
                    mainHandler.post(this::finishCheck);
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
        btnStart.setText(R.string.start_check);
        progressBar.setVisibility(View.GONE);
        boolean has = !workingConfigs.isEmpty();
        btnSave.setEnabled(has);
        btnCopyTop.setEnabled(has);
        if (has) {
            statusText.setText("Готово! Рабочих: " + workingConfigs.size());
        }
    }

    private List<String> fetchLines(String urlStr) throws Exception {
        List<String> lines = new ArrayList<>();
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "LetoVPN-Checker/1.0");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void saveAllToFile() {
        try {
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
            if (dir == null) dir = getFilesDir();
            File file = new File(dir, "letovpn_working_configs.txt");

            try (FileWriter writer = new FileWriter(file)) {
                for (ConfigItem item : workingConfigs) {
                    writer.write(item.raw + "\n");
                }
            }

            Toast.makeText(this, "Сохранено: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void copyTop10() {
        StringBuilder sb = new StringBuilder();
        int count = Math.min(10, workingConfigs.size());
        for (int i = 0; i < count; i++) {
            sb.append(workingConfigs.get(i).raw).append("\n");
        }

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("top10", sb.toString()));
        Toast.makeText(this, "Скопировано топ-" + count, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRunning = false;
        executor.shutdownNow();
    }
}
