package com.letovpn.checker;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class XrayEngine {

    private static final String TAG = "XrayEngine";
    private static final AtomicBoolean downloading = new AtomicBoolean(false);
    private static volatile boolean ready = false;

    private XrayEngine() {}

    public static synchronized boolean ensureBinary(Context ctx) {
        File bin = binaryFile(ctx);
        if (bin.exists() && bin.canExecute() && bin.length() > 10000) {
            ready = true;
            return true;
        }
        if (!downloading.compareAndSet(false, true)) {
            int wait = 0;
            while (downloading.get() && wait < 120) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                wait++;
                if (bin.exists() && bin.canExecute()) {
                    ready = true;
                    return true;
                }
            }
            return ready;
        }
        try {
            String abi = Build.SUPPORTED_ABIS[0];
            String asset;
            if (abi.contains("arm64")) asset = "Xray-android-arm64-v8a.zip";
            else if (abi.contains("armeabi")) asset = "Xray-android-arm32-v7a.zip";
            else if (abi.contains("x86_64")) asset = "Xray-android-amd64.zip";
            else asset = "Xray-android-arm64-v8a.zip";

            String url = "https://github.com/XTLS/Xray-core/releases/download/v25.3.6/" + asset;
            File zip = new File(ctx.getFilesDir(), "xray.zip");

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestProperty("User-Agent", "LetoVPN-Checker/1.6");

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 FileOutputStream out = new FileOutputStream(zip)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }

            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(
                    new java.io.FileInputStream(zip)))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.endsWith("xray") || name.equals("xray")) {
                        try (FileOutputStream fos = new FileOutputStream(bin)) {
                            byte[] buf = new byte[8192];
                            int n;
                            while ((n = zis.read(buf)) != -1) fos.write(buf, 0, n);
                        }
                        break;
                    }
                }
            }
            //noinspection ResultOfMethodCallIgnored
            zip.delete();

            if (!bin.exists()) return false;
            //noinspection ResultOfMethodCallIgnored
            bin.setExecutable(true, false);
            ready = bin.canExecute();
            return ready;
        } catch (Exception e) {
            Log.e(TAG, "download failed", e);
            return false;
        } finally {
            downloading.set(false);
        }
    }

    private static File binaryFile(Context ctx) {
        return new File(ctx.getFilesDir(), "xray");
    }

    /**
     * @param speedTest true = Максимум (Xray Speed): скачиваем данные через туннель
     */
    public static long test(Context ctx, ConfigItem item, boolean speedTest) {
        if (!item.raw.startsWith("vless://")) {
            return ConfigChecker.test(item, ConfigChecker.Method.TCP_DNS, ctx);
        }
        if (!ensureBinary(ctx)) return -1;

        int localPort = 18000 + (int) (Math.random() * 2000);
        File cfg = new File(ctx.getCacheDir(), "xray_" + localPort + ".json");
        Process proc = null;

        try {
            String json = buildVlessConfig(item, localPort);
            try (FileOutputStream fos = new FileOutputStream(cfg)) {
                fos.write(json.getBytes("UTF-8"));
            }

            ProcessBuilder pb = new ProcessBuilder(
                    binaryFile(ctx).getAbsolutePath(), "run", "-c", cfg.getAbsolutePath());
            pb.redirectErrorStream(true);
            pb.directory(ctx.getFilesDir());
            proc = pb.start();

            Thread.sleep(speedTest ? 1000 : 800);

            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", localPort));
            OkHttpClient client = new OkHttpClient.Builder()
                    .proxy(proxy)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(speedTest ? 15 : 8, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .build();

            long start = System.currentTimeMillis();

            if (speedTest) {
                // Реальное скачивание ~100KB через туннель — точнее чем HEAD
                Request req = new Request.Builder()
                        .url("https://speed.cloudflare.com/__down?bytes=102400")
                        .get()
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (!resp.isSuccessful()) return -1;
                    ResponseBody body = resp.body();
                    if (body == null) return -1;
                    byte[] data = body.bytes();
                    if (data.length < 1000) return -1;
                    long elapsed = Math.max(1, System.currentTimeMillis() - start);
                    // latency-like score: время скачивания (меньше = лучше)
                    return elapsed;
                }
            } else {
                Request req = new Request.Builder()
                        .url("https://www.gstatic.com/generate_204")
                        .head()
                        .build();
                try (Response resp = client.newCall(req).execute()) {
                    if (resp.code() == 204 || resp.isSuccessful()) {
                        return Math.max(1, System.currentTimeMillis() - start);
                    }
                }
                return -1;
            }
        } catch (Exception e) {
            Log.e(TAG, "xray test fail", e);
            return -1;
        } finally {
            if (proc != null) {
                try {
                    proc.destroy();
                    if (Build.VERSION.SDK_INT >= 26) proc.destroyForcibly();
                } catch (Exception ignored) {}
            }
            //noinspection ResultOfMethodCallIgnored
            cfg.delete();
        }
    }

    private static String buildVlessConfig(ConfigItem item, int localPort) {
        String uuid = extractUuid(item.raw);
        String query = "";
        int q = item.raw.indexOf('?');
        int h = item.raw.indexOf('#');
        if (q > 0) {
            query = h > q ? item.raw.substring(q + 1, h) : item.raw.substring(q + 1);
        }

        String security = param(query, "security", "none");
        String sni = param(query, "sni", item.host);
        String fp = param(query, "fp", "chrome");
        String pbk = param(query, "pbk", "");
        String sid = param(query, "sid", "");
        String spx = param(query, "spx", "");
        String type = param(query, "type", "tcp");
        String path = param(query, "path", "/");
        String hostHeader = param(query, "host", sni);
        String flow = param(query, "flow", "");

        StringBuilder stream = new StringBuilder();
        stream.append("\"network\":\"").append(esc(type)).append("\"");

        if ("ws".equals(type)) {
            stream.append(",\"wsSettings\":{\"path\":\"").append(esc(path))
                    .append("\",\"headers\":{\"Host\":\"").append(esc(hostHeader)).append("\"}}");
        }

        if ("reality".equals(security)) {
            stream.append(",\"security\":\"reality\"")
                    .append(",\"realitySettings\":{")
                    .append("\"serverName\":\"").append(esc(sni)).append("\",")
                    .append("\"fingerprint\":\"").append(esc(fp)).append("\",")
                    .append("\"publicKey\":\"").append(esc(pbk)).append("\",")
                    .append("\"shortId\":\"").append(esc(sid)).append("\",")
                    .append("\"spiderX\":\"").append(esc(spx)).append("\"}");
        } else if ("tls".equals(security)) {
            stream.append(",\"security\":\"tls\"")
                    .append(",\"tlsSettings\":{\"serverName\":\"").append(esc(sni))
                    .append("\",\"fingerprint\":\"").append(esc(fp)).append("\"}");
        } else {
            stream.append(",\"security\":\"none\"");
        }

        String user = "{\"id\":\"" + esc(uuid) + "\",\"encryption\":\"none\"";
        if (!flow.isEmpty()) user += ",\"flow\":\"" + esc(flow) + "\"";
        user += "}";

        return "{"
                + "\"log\":{\"loglevel\":\"warning\"},"
                + "\"inbounds\":[{\"port\":" + localPort
                + ",\"listen\":\"127.0.0.1\",\"protocol\":\"socks\","
                + "\"settings\":{\"udp\":true}}],"
                + "\"outbounds\":[{"
                + "\"protocol\":\"vless\","
                + "\"settings\":{\"vnext\":[{"
                + "\"address\":\"" + esc(item.host) + "\","
                + "\"port\":" + item.port + ","
                + "\"users\":[" + user + "]}]},"
                + "\"streamSettings\":{" + stream + "}"
                + "}]}";
    }

    private static String extractUuid(String raw) {
        try {
            int start = raw.indexOf("://") + 3;
            int at = raw.indexOf('@', start);
            if (at > start) return raw.substring(start, at);
        } catch (Exception ignored) {}
        return "";
    }

    private static String param(String query, String key, String def) {
        if (query == null || query.isEmpty()) return def;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equalsIgnoreCase(key)) {
                try {
                    return java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8");
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return def;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
