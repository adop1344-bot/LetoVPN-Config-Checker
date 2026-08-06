package com.letovpn.checker;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConfigChecker {

    public enum Mode { TCP, PROXY_GET }

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

    public static long test(ConfigItem item, Mode mode) {
        if (item.host == null || item.host.isEmpty() || item.port <= 0) return -1;

        if (mode == Mode.TCP) {
            return testTcp(item.host, item.port);
        } else {
            return testProxyGet(item);
        }
    }

    private static long testTcp(String host, int port) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 4000);
            long latency = System.currentTimeMillis() - start;
            return latency;
        } catch (Exception e) {
            return -1;
        }
    }

    // Via Proxy GET — simplified: we try a fast HTTP GET to a known endpoint
    // (real proxy tunneling would require full xray core)
    private static long testProxyGet(ConfigItem item) {
        // Fallback to TCP + extra HTTP latency test to a public endpoint
        long tcp = testTcp(item.host, item.port);
        if (tcp < 0) return -1;

        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url("https://www.google.com/generate_204")
                    .head()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                // just measure if network works; real proxy would use the config
                return tcp + (System.currentTimeMillis() - start);
            }
        } catch (IOException e) {
            return tcp; // still return TCP if HTTP fails
        }
    }
}
