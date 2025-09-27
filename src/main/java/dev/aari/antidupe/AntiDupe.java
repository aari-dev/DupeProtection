package dev.aari.antidupe;

import dev.aari.antidupe.commands.AdminCommand;
import dev.aari.antidupe.commands.IdCommand;
import dev.aari.antidupe.commands.ItemCommand;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import dev.aari.antidupe.listeners.ItemTrackingListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AntiDupe extends JavaPlugin {

    private ConfigManager configManager;
    private ItemRegistry itemRegistry;
    private ItemTrackingListener trackingListener;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.itemRegistry = new ItemRegistry(this);
        this.trackingListener = new ItemTrackingListener(itemRegistry, configManager);

        CompletableFuture.runAsync(() -> itemRegistry.initialize())
                .thenRun(() -> getServer().getScheduler().runTask(this, this::registerComponents))
                .exceptionally(throwable -> {
                    getSLF4JLogger().error("Failed to initialize ItemRegistry", throwable);
                    getServer().getPluginManager().disablePlugin(this);
                    return null;
                });
    }

    @Override
    public void onDisable() {
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

        Objects.requireNonNull(getCommand("id")).setExecutor(new IdCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("item")).setExecutor(new ItemCommand(itemRegistry, configManager));
        Objects.requireNonNull(getCommand("antidupe")).setExecutor(new AdminCommand(itemRegistry, configManager));

        getSLF4JLogger().info("AntiDupe initialized successfully");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ItemRegistry getItemRegistry() {
        return itemRegistry;
    }
}