package com.letovpn.checker;

import android.content.Context;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ConfigChecker {

    public enum Method {
        TCP,
        TCP_DNS,
        PROXY_GET,
        DEEP,
        XRAY,
        XRAY_SPEED  // точнее Xray: скачивание через туннель
    }

    public static String methodName(Method m) {
        switch (m) {
            case TCP:        return "Неточная (TCP)";
            case TCP_DNS:    return "Средняя (TCP+DNS)";
            case PROXY_GET:  return "Точная (Via Proxy GET)";
            case DEEP:       return "Суперточная (Deep)";
            case XRAY:       return "Xray (ядро)";
            case XRAY_SPEED: return "Максимум (Xray Speed)";
            default:         return "?";
        }
    }

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .build();

    public static long test(ConfigItem item, Method method, Context ctx) {
        if (item.host == null || item.host.isEmpty() || item.port <= 0) return -1;

        switch (method) {
            case TCP:        return tcpConnect(item.host, item.port, 2500);
            case TCP_DNS:    return tcpWithDns(item.host, item.port);
            case PROXY_GET:  return viaProxyGet(item.host, item.port);
            case DEEP:       return deepScan(item.host, item.port);
            case XRAY:       return XrayEngine.test(ctx, item, false);
            case XRAY_SPEED: return XrayEngine.test(ctx, item, true);
            default:         return tcpConnect(item.host, item.port, 3000);
        }
    }

    private static long tcpConnect(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            if (!socket.isConnected()) return -1;
            return Math.max(1, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return -1;
        }
    }

    private static long tcpWithDns(String host, int port) {
        long start = System.currentTimeMillis();
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs == null || addrs.length == 0) return -1;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(addrs[0], port), 3500);
                if (!socket.isConnected()) return -1;
                return Math.max(1, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            return -1;
        }
    }

    private static long viaProxyGet(String host, int port) {
        long tcp = tcpConnect(host, port, 4000);
        if (tcp < 0) return -1;
        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url("https://www.gstatic.com/generate_204").head().build();
            try (Response response = httpClient.newCall(request).execute()) {
                return tcp + Math.max(0, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            return tcp;
        }
    }

    private static long deepScan(String host, int port) {
        InetAddress addr;
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs == null || addrs.length == 0) return -1;
            addr = addrs[0];
        } catch (Exception e) {
            return -1;
        }

        long bestTcp = Long.MAX_VALUE;
        boolean tcpOk = false;
        for (int i = 0; i < 2; i++) {
            long t0 = System.currentTimeMillis();
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(addr, port), 4500);
                if (socket.isConnected()) {
                    long lat = System.currentTimeMillis() - t0;
                    if (lat < bestTcp) bestTcp = lat;
                    tcpOk = true;
                }
            } catch (Exception ignored) {}
        }
        if (!tcpOk) return -1;

        long httpExtra = 0;
        try {
            long h0 = System.currentTimeMillis();
            Request request = new Request.Builder()
                    .url("https://cp.cloudflare.com/generate_204").head().build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 204 || response.isSuccessful()) {
                    httpExtra = System.currentTimeMillis() - h0;
                }
            }
        } catch (Exception ignored) {}

        return bestTcp + httpExtra;
    }
}
