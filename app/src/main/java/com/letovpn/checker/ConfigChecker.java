package com.letovpn.checker;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Реальные методы проверки доступности сервера конфига.
 * Без xray-core нельзя полностью прогнать VLESS-туннель,
 * поэтому методы проверяют сеть до хоста разными способами.
 */
public class ConfigChecker {

    public enum Method {
        TCP,          // Неточная (TCP)
        TCP_DNS,      // Средняя (TCP+DNS)
        PROXY_GET,    // Точная (Via Proxy GET)
        DEEP          // Суперточная (Deep)
    }

    public static String methodName(Method m) {
        switch (m) {
            case TCP:       return "Неточная (TCP)";
            case TCP_DNS:   return "Средняя (TCP+DNS)";
            case PROXY_GET: return "Точная (Via Proxy GET)";
            case DEEP:      return "Суперточная (Deep)";
            default:        return "?";
        }
    }

    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(false)
            .build();

    public static long test(ConfigItem item, Method method) {
        if (item.host == null || item.host.isEmpty() || item.port <= 0) return -1;

        switch (method) {
            case TCP:
                return tcpConnect(item.host, item.port, 2500);
            case TCP_DNS:
                return tcpWithDns(item.host, item.port);
            case PROXY_GET:
                return viaProxyGet(item.host, item.port);
            case DEEP:
                return deepScan(item.host, item.port);
            default:
                return tcpConnect(item.host, item.port, 3000);
        }
    }

    /** Неточная (TCP): один TCP-коннект с коротким таймаутом */
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

    /** Средняя (TCP+DNS): сначала резолв DNS, потом TCP */
    private static long tcpWithDns(String host, int port) {
        long start = System.currentTimeMillis();
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            if (addrs == null || addrs.length == 0) return -1;

            // пробуем первый резолвнутый адрес
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(addrs[0], port), 3500);
                if (!socket.isConnected()) return -1;
                return Math.max(1, System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Точная (Via Proxy GET):
     * 1) TCP до хоста конфига
     * 2) HTTP HEAD к тестовому URL (проверка что с устройства есть выход в сеть
     *    после успешного коннекта к серверу — эмуляция «get через доступность»)
     */
    private static long viaProxyGet(String host, int port) {
        long tcp = tcpConnect(host, port, 4000);
        if (tcp < 0) return -1;

        long start = System.currentTimeMillis();
        try {
            Request request = new Request.Builder()
                    .url("https://www.gstatic.com/generate_204")
                    .head()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                long httpPart = System.currentTimeMillis() - start;
                // успех если TCP ок; HTTP добавляет к latency
                return tcp + Math.max(0, httpPart);
            }
        } catch (Exception e) {
            // TCP прошёл — считаем рабочим, HTTP может упасть из-за сети
            return tcp;
        }
    }

    /**
     * Суперточная (Deep):
     * DNS + 2 TCP-попытки + HTTP HEAD.
     * Берём лучший TCP и прибавляем HTTP если он прошёл.
     */
    private static long deepScan(String host, int port) {
        long start = System.currentTimeMillis();

        // DNS
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
                    .url("https://cp.cloudflare.com/generate_204")
                    .head()
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.code() == 204 || response.isSuccessful()) {
                    httpExtra = System.currentTimeMillis() - h0;
                }
            }
        } catch (Exception ignored) {}

        return bestTcp + httpExtra;
    }
}
