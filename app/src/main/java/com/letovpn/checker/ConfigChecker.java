package com.letovpn.checker;

import java.net.InetSocketAddress;
import java.net.Socket;

public class ConfigChecker {

    public enum Method {
        FAST,       // Неточная (Молния)
        BALANCED,   // Средняя (Баланс)
        ACCURATE,   // Точная (Радар)
        PRECISE     // Суперточная (Снайпер)
    }

    public static String methodName(Method m) {
        switch (m) {
            case FAST:     return "Неточная (Молния)";
            case BALANCED: return "Средняя (Баланс)";
            case ACCURATE: return "Точная (Радар)";
            case PRECISE:  return "Суперточная (Снайпер)";
            default:       return "?";
        }
    }

    public static long test(ConfigItem item, Method method) {
        if (item.host == null || item.host.isEmpty() || item.port <= 0) return -1;

        switch (method) {
            case FAST:
                return testOnce(item.host, item.port, 1500);
            case BALANCED:
                return testOnce(item.host, item.port, 3000);
            case ACCURATE:
                return testAverage(item.host, item.port, 4500, 2);
            case PRECISE:
                return testBestOf(item.host, item.port, 6000, 3);
            default:
                return testOnce(item.host, item.port, 3000);
        }
    }

    private static long testOnce(String host, int port, int timeoutMs) {
        long start = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }

    private static long testAverage(String host, int port, int timeoutMs, int attempts) {
        long total = 0;
        int success = 0;
        for (int i = 0; i < attempts; i++) {
            long r = testOnce(host, port, timeoutMs);
            if (r > 0) {
                total += r;
                success++;
            }
        }
        return success > 0 ? total / success : -1;
    }

    private static long testBestOf(String host, int port, int timeoutMs, int attempts) {
        long best = Long.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < attempts; i++) {
            long r = testOnce(host, port, timeoutMs);
            if (r > 0) {
                any = true;
                if (r < best) best = r;
            }
        }
        return any ? best : -1;
    }
}
