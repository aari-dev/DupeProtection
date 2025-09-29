package dev.aari.antidupe;

import dev.aari.antidupe.commands.AdminCommand;
import dev.aari.antidupe.commands.AnnounceCommand;
import dev.aari.antidupe.commands.DupeCommand;
import dev.aari.antidupe.commands.IdCommand;
import dev.aari.antidupe.commands.ItemCommand;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.listeners.AdvancedProtectionListener;
import dev.aari.antidupe.listeners.AntiCheatListener;
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
    private AdvancedProtectionListener protectionListener;
    private AntiCheatListener antiCheatListener;
    private DupeDebugManager dupeDebugManager;
    private RedisManager redisManager;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.itemRegistry = new ItemRegistry(this);
        this.redisManager = new RedisManager(configManager, itemRegistry);
        this.dupeDebugManager = new DupeDebugManager(this, configManager);
        this.trackingListener = new ItemTrackingListener(itemRegistry, configManager, dupeDebugManager);
        this.protectionListener = new AdvancedProtectionListener(this, configManager, dupeDebugManager);
        this.antiCheatListener = new AntiCheatListener(this, configManager, dupeDebugManager);

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
        if (protectionListener != null) {
            protectionListener.cleanup();
        }
        if (antiCheatListener != null) {
            antiCheatListener.cleanup();
        }
    }

    private void registerComponents() {
        getServer().getPluginManager().registerEvents(trackingListener, this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(antiCheatListener, this);
        getServer().getPluginManager().registerEvents(dupeDebugManager, this);

        Objects.requireNonNull(getCommand("id")).setExecutor(new IdCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("item")).setExecutor(new ItemCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("antidupe")).setExecutor(new AdminCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("dupe")).setExecutor(new DupeCommand(dupeDebugManager, itemRegistry, configManager));
        Objects.requireNonNull(getCommand("dupe")).setTabCompleter(new DupeCommand(dupeDebugManager, itemRegistry, configManager));
        Objects.requireNonNull(getCommand("announce")).setExecutor(new AnnounceCommand(configManager));

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
/*
TODO: Remove console spam (temp) and add final webhook logging
TODO: Clean up the code, this has potential. Take the proj more serious
TODO: Add punishment system, staff commands, chat filter, @mentions in chat, OP whitelist
TODO: Expand from AntiDupe/Light AC to advanced staff & security plugin
TODO: Advanced staff sys includes staff mode, chat moderation, punishments, utils
TODO: Add a /discord, /media, /trim (/hex), /sign {message}, /baltop, /giveawy {amount}, basic utils but advanced
TODO: Create license system (for future when all features added)
TODO: Advertise & make .png description for BBB
TODO: GIF previews of the plugin
TODO: /media add|remove , /staff add|promote|demote|remove , /rank add|remove {rank}
TODO: Add warps, /setspawn , /spawn, /setpvp, /pvp
TODO: Add simple features such as cookie clicker
TODO: Make all of the above toggleable and configureable, essentially making a core but with more security and utility based features.
TODO: Make the config not look like a 5 years olds ass
 */