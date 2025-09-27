package dev.aari.antidupe.config;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigManager {

    private final AntiDupe plugin;
    private final File configFile;
    private final ConcurrentHashMap<String, Component> messageCache;
    private volatile FileConfiguration config;

    public ConfigManager(AntiDupe plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        this.messageCache = new ConcurrentHashMap<>(32, 0.75f, 4);
        loadConfig();
    }

    private void loadConfig() {
        try {
            if (!configFile.exists()) {
                createDefaultConfig();
            }
            this.config = YamlConfiguration.loadConfiguration(configFile);
            cacheMessages();
        } catch (IOException e) {
            plugin.getSLF4JLogger().error("Failed to load config", e);
            this.config = new YamlConfiguration();
        }
    }

    private void createDefaultConfig() throws IOException {
        plugin.getDataFolder().mkdirs();

        try (InputStream defaultConfig = plugin.getResource("config.yml")) {
            if (defaultConfig != null) {
                Files.copy(defaultConfig, configFile.toPath());
            } else {
                createFallbackConfig();
            }
        }
    }

    private void createFallbackConfig() throws IOException {
        YamlConfiguration defaultConfig = new YamlConfiguration();

        defaultConfig.set("messages.no-permission", "&#ff4757No permission.");
        defaultConfig.set("messages.console-cannot-check", "&#ff4757Console cannot check items.");
        defaultConfig.set("messages.hold-item", "&#ff4757Hold an item to check.");
        defaultConfig.set("messages.invalid-item", "&#ff4757Invalid item.");
        defaultConfig.set("messages.item-id", "&#747d8cItem ID: &#ffa502{id}");
        defaultConfig.set("messages.duplicates-warning", "&#ff4757⚠ WARNING: &#ffa502{count} potential duplicates found!");
        defaultConfig.set("messages.error-checking", "&#ff4757Error checking item.");
        defaultConfig.set("messages.usage-id", "&#ff4757Usage: /id check | /id lookup <id>");
        defaultConfig.set("messages.invalid-id-format", "&#ff4757Invalid ID format.");
        defaultConfig.set("messages.item-not-found", "&#ff4757Item not found.");
        defaultConfig.set("messages.original-item", "&#747d8cOriginal Item &#ffa502{id} &#747d8ccreated by &#ffffffu{creator}");
        defaultConfig.set("messages.no-duplicates", "&#2ed573No duplicates found.");
        defaultConfig.set("messages.duplicates-found", "&#ff4757Found &#ffa502{count} &#ff4757duplicates:");
        defaultConfig.set("messages.duplicate-entry", "&#747d8c• ID &#ff4757{id} &#747d8cby &#ffffff{creator}");
        defaultConfig.set("messages.error-lookup", "&#ff4757Error during lookup.");
        defaultConfig.set("messages.usage-item", "&#ff4757Usage: /item history [id]");
        defaultConfig.set("messages.hold-item-or-specify", "&#ff4757Hold an item or specify ID.");
        defaultConfig.set("messages.no-tracking-id", "&#ff4757Item has no tracking ID.");
        defaultConfig.set("messages.console-specify-id", "&#ff4757Console must specify ID.");
        defaultConfig.set("messages.error-history", "&#ff4757Error retrieving history.");
        defaultConfig.set("messages.history-for-item", "&#747d8cHistory for Item &#ffa502{id}");
        defaultConfig.set("messages.created-by", "&#747d8cCreated: &#ffffff{time} &#747d8cby &#ffffff{creator}");
        defaultConfig.set("messages.no-actions", "&#747d8cNo actions recorded.");
        defaultConfig.set("messages.actions-header", "&#747d8cActions:");
        defaultConfig.set("messages.action-entry", "&#747d8c• &#ffffff{time} &#747d8c- &#ffa502{action} &#747d8cby &#ffffff{player}");
        defaultConfig.set("messages.more-actions", "&#747d8c... and &#ffa502{count} &#747d8cmore actions");

        defaultConfig.set("settings.action-throttle-ms", 100);
        defaultConfig.set("settings.max-history-display", 10);
        defaultConfig.set("settings.inventory-scan-chance", 20);

        defaultConfig.save(configFile);
    }

    private void cacheMessages() {
        messageCache.clear();
        if (config.isConfigurationSection("messages")) {
            for (String key : config.getConfigurationSection("messages").getKeys(false)) {
                String message = config.getString("messages." + key, "");
                messageCache.put(key, ColorUtil.translateColorCodes(message));
            }
        }
    }

    public Component getMessage(String key, Object... placeholders) {
        Component message = messageCache.get(key);
        if (message == null) {
            return ColorUtil.translateColorCodes("&#ff4757Missing message: " + key);
        }

        if (placeholders.length == 0) {
            return message;
        }

        String rawMessage = config.getString("messages." + key, "");
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = "{" + placeholders[i] + "}";
                String value = String.valueOf(placeholders[i + 1]);
                rawMessage = rawMessage.replace(placeholder, value);
            }
        }

        return ColorUtil.translateColorCodes(rawMessage);
    }

    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    public long getLong(String path, long defaultValue) {
        return config.getLong(path, defaultValue);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    public void reload() {
        loadConfig();
        plugin.getSLF4JLogger().info("Configuration reloaded");
    }
}