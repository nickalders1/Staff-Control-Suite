package com.staffcontrol.proxy.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ProxyConfig {

    private final Path dataDirectory;

    private int apiPort = 8080;
    private int agentPort = 8081;
    private String apiToken = "CHANGE_ME";
    private String agentToken = "CHANGE_ME_AGENT";
    private List<String> allowedIps = new ArrayList<>();
    private String proxyName = "MyProxy";
    private String databaseType = "sqlite";
    private String databasePath = "staffcontrol.db";
    private int sessionExpiryHours = 24;

    private String brandingAppTitle = "Staff Control";
    private String brandingNetworkName = "My Network";
    private String brandingLogoFile = "logo.png";
    private String brandingAccentColor = "#7AA2FF";

    public ProxyConfig(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public Path getDataDirectory() { return dataDirectory; }

    public void load() throws IOException {
        Path configFile = dataDirectory.resolve("config.yml");

        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        if (!Files.exists(configFile)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile);
                } else {
                    save();
                }
            }
        }

        Yaml yaml = new Yaml();
        try (InputStream in = Files.newInputStream(configFile)) {
            Map<String, Object> data = yaml.load(in);
            if (data != null) {
                apiPort = getInt(data, "apiPort", 8080);
                agentPort = getInt(data, "agentPort", 8081);
                apiToken = getString(data, "apiToken", "CHANGE_ME");
                agentToken = getString(data, "agentToken", "CHANGE_ME_AGENT");
                proxyName = getString(data, "proxyName", "MyProxy");
                databaseType = getString(data, "databaseType", "sqlite");
                databasePath = getString(data, "databasePath", "staffcontrol.db");
                sessionExpiryHours = getInt(data, "sessionExpiryHours", 24);
                Object ips = data.get("allowedIps");
                if (ips instanceof List<?>) {
                    allowedIps = new ArrayList<>();
                    for (Object ip : (List<?>) ips) {
                        allowedIps.add(String.valueOf(ip));
                    }
                }
                Object brandingSection = data.get("branding");
                if (brandingSection instanceof Map<?, ?> bm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> b = (Map<String, Object>) bm;
                    brandingAppTitle   = getString(b, "appTitle",    "Staff Control");
                    brandingNetworkName = getString(b, "networkName", "My Network");
                    brandingLogoFile   = getString(b, "logoFile",    "logo.png");
                    brandingAccentColor = getString(b, "accentColor", "#7AA2FF");
                }
            }
        }

        boolean changed = false;

        if ("CHANGE_ME".equals(apiToken)) {
            apiToken = UUID.randomUUID().toString();
            changed = true;
        }

        if ("CHANGE_ME_AGENT".equals(agentToken)) {
            agentToken = UUID.randomUUID().toString();
            changed = true;
        }

        if (changed) {
            save();
        }
    }

    public void save() throws IOException {
        Path configFile = dataDirectory.resolve("config.yml");

        if (!Files.exists(dataDirectory)) {
            Files.createDirectories(dataDirectory);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apiPort", apiPort);
        data.put("agentPort", agentPort);
        data.put("apiToken", apiToken);
        data.put("agentToken", agentToken);
        data.put("allowedIps", allowedIps);
        data.put("proxyName", proxyName);
        data.put("databaseType", databaseType);
        data.put("databasePath", databasePath);
        data.put("sessionExpiryHours", sessionExpiryHours);

        Map<String, Object> branding = new LinkedHashMap<>();
        branding.put("appTitle",    brandingAppTitle);
        branding.put("networkName", brandingNetworkName);
        branding.put("logoFile",    brandingLogoFile);
        branding.put("accentColor", brandingAccentColor);
        data.put("branding", branding);

        try (Writer writer = Files.newBufferedWriter(configFile)) {
            yaml.dump(data, writer);
        }
    }

    private int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return def;
    }

    private String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return (val != null && !"null".equals(String.valueOf(val))) ? String.valueOf(val) : def;
    }

    public int getApiPort() { return apiPort; }
    public int getAgentPort() { return agentPort; }
    public String getApiToken() { return apiToken; }
    public String getAgentToken() { return agentToken; }
    public List<String> getAllowedIps() { return allowedIps; }
    public String getProxyName() { return proxyName; }
    public String getDatabaseType() { return databaseType; }
    public String getDatabasePath() { return databasePath; }
    public int getSessionExpiryHours() { return sessionExpiryHours; }

    public void setApiPort(int apiPort) { this.apiPort = apiPort; }
    public void setAgentPort(int agentPort) { this.agentPort = agentPort; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    public void setAgentToken(String agentToken) { this.agentToken = agentToken; }
    public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps; }
    public void setProxyName(String proxyName) { this.proxyName = proxyName; }
    public void setDatabaseType(String databaseType) { this.databaseType = databaseType; }
    public void setDatabasePath(String databasePath) { this.databasePath = databasePath; }
    public void setSessionExpiryHours(int sessionExpiryHours) { this.sessionExpiryHours = sessionExpiryHours; }

    public String getBrandingAppTitle()    { return brandingAppTitle; }
    public String getBrandingNetworkName() { return brandingNetworkName; }
    public String getBrandingLogoFile()    { return brandingLogoFile; }
    public String getBrandingAccentColor() { return brandingAccentColor; }

    public void setBrandingAppTitle(String v)    { this.brandingAppTitle = v; }
    public void setBrandingNetworkName(String v) { this.brandingNetworkName = v; }
    public void setBrandingLogoFile(String v)    { this.brandingLogoFile = v; }
    public void setBrandingAccentColor(String v) { this.brandingAccentColor = v; }
}
