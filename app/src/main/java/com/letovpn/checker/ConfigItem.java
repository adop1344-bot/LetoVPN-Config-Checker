package com.letovpn.checker;

public class ConfigItem {
    public String raw;
    public String name;
    public String host;
    public int port;
    public long latency = -1; // ms, -1 = fail
    public boolean working = false;

    public ConfigItem(String raw) {
        this.raw = raw.trim();
        parse();
    }

    private void parse() {
        try {
            // vless://uuid@host:port?params#name
            String s = raw;
            if (s.startsWith("vless://") || s.startsWith("vmess://") || s.startsWith("trojan://")) {
                int at = s.indexOf('@');
                int q = s.indexOf('?', at);
                int hash = s.indexOf('#');

                if (at > 0) {
                    String hostPort = (q > at) ? s.substring(at + 1, q) : (hash > at ? s.substring(at + 1, hash) : s.substring(at + 1));
                    if (hostPort.contains(":")) {
                        String[] hp = hostPort.split(":");
                        this.host = hp[0];
                        this.port = Integer.parseInt(hp[1].replaceAll("[^0-9]", ""));
                    } else {
                        this.host = hostPort;
                        this.port = 443;
                    }
                }

                if (hash > 0 && hash < s.length() - 1) {
                    this.name = java.net.URLDecoder.decode(s.substring(hash + 1), "UTF-8");
                } else {
                    this.name = (host != null ? host : "unknown");
                }
            } else {
                this.name = "invalid";
            }
        } catch (Exception e) {
            this.name = "parse error";
            this.host = "";
            this.port = 0;
        }
    }
}
