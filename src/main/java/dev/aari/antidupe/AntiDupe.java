package dev.aari.antidupe;

import dev.aari.antidupe.commands.AdminCommand;
import dev.aari.antidupe.commands.DupeCommand;
import dev.aari.antidupe.commands.IdCommand;
import dev.aari.antidupe.commands.ItemCommand;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.listeners.ItemTrackingListener;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.redis.RedisManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AntiDupe extends JavaPlugin {

    private ConfigManager configManager;
    private ItemRegistry itemRegistry;
    private ItemTrackingListener trackingListener;
    private DupeDebugManager dupeDebugManager;
    private RedisManager redisManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.itemRegistry = new ItemRegistry(this);
        this.redisManager = new RedisManager(configManager, itemRegistry);
        this.dupeDebugManager = new DupeDebugManager(this, configManager);
        this.trackingListener = new ItemTrackingListener(itemRegistry, configManager, dupeDebugManager);

        CompletableFuture.runAsync(() -> itemRegistry.initialize())
                .thenRun(() -> getServer().getScheduler().runTask(this, this::registerComponents))
                .exceptionally(throwable -> {
                    getSLF4JLogger().error("Failed to initialize ItemRegistry", throwable);
                    getServer().getPluginManager().disablePlugin(this);
                    return null;
                });

        if (redisManager.isEnabled()) {
            if (redisManager.isConnected()) {
                getSLF4JLogger().info("Redis connection established - Cross-proxy support enabled");
            } else {
                getSLF4JLogger().warn("Redis is enabled but connection failed - Running in single-server mode");
            }
        }
    }

    @Override
    public void onDisable() {
        if (redisManager != null) {
            redisManager.close();
        }
        if (itemRegistry != null) {
            CompletableFuture.runAsync(itemRegistry::shutdown)
                    .exceptionally(throwable -> {
                        getSLF4JLogger().error("Error during shutdown", throwable);
                        return null;
                    });
        }
        if (trackingListener != null) {
            trackingListener.cleanup();
        }
    }

    private void registerComponents() {
        getServer().getPluginManager().registerEvents(trackingListener, this);
        getServer().getPluginManager().registerEvents(dupeDebugManager, this);

        Objects.requireNonNull(getCommand("id")).setExecutor(new IdCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("item")).setExecutor(new ItemCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("antidupe")).setExecutor(new AdminCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("dupe")).setExecutor(new DupeCommand(dupeDebugManager, itemRegistry, configManager));
        Objects.requireNonNull(getCommand("dupe")).setTabCompleter(new DupeCommand(dupeDebugManager, itemRegistry, configManager));

        getSLF4JLogger().info("AntiDupe initialized successfully");
    }

    public DupeDebugManager getDupeDebugManager() {
        return dupeDebugManager;
    }

    public RedisManager getRedisManager() {
        return redisManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }
}