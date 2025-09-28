package dev.aari.antidupe.redis;

import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.data.ItemRegistry;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class RedisManager {

    private final JedisPool jedisPool;
    private final ConfigManager config;
    private final ItemRegistry itemRegistry;
    private final DupeAlertSubscriber alertSubscriber;

    public RedisManager(ConfigManager config, ItemRegistry itemRegistry) {
        this.config = config;
        this.itemRegistry = itemRegistry;
        this.jedisPool = createJedisPool();
        this.alertSubscriber = new DupeAlertSubscriber(itemRegistry);

        if (isEnabled()) {
            subscribeToAlerts();
        }
    }

    private JedisPool createJedisPool() {
        if (!isEnabled()) return null;

        String host = config.getString("redis.host", "localhost");
        int port = config.getInt("redis.port", 6379);
        String password = config.getString("redis.password", "");
        int database = config.getInt("redis.database", 0);
        int timeout = config.getInt("redis.timeout", 2000);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getInt("redis.pool.max-total", 8));
        poolConfig.setMaxIdle(config.getInt("redis.pool.max-idle", 4));
        poolConfig.setMinIdle(config.getInt("redis.pool.min-idle", 1));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);

        if (password.isEmpty()) {
            return new JedisPool(poolConfig, host, port, timeout, null, database);
        } else {
            return new JedisPool(poolConfig, host, port, timeout, password, database);
        }
    }

    public boolean isEnabled() {
        return config.getBoolean("redis.enabled", false);
    }

    public CompletableFuture<Void> publishDupeAlert(String serverName, String playerName, long itemId, int duplicateCount) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String alertData = String.format("%s|%s|%d|%d", serverName, playerName, itemId, duplicateCount);
                jedis.publish("antidupe:alerts", alertData);
            } catch (Exception e) {
                // Log error but don't fail
                System.err.println("Redis publish failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> syncItemData(long itemId, String fingerprint, String creator, long timestamp) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String itemData = String.format("%s|%s|%d", fingerprint, creator, timestamp);
                jedis.hset("antidupe:items", String.valueOf(itemId), itemData);
                jedis.expire("antidupe:items", config.getInt("redis.item-expire", 86400));
            } catch (Exception e) {
                System.err.println("Redis item sync failed: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<String>> getNetworkDuplicates(String fingerprint) {
        if (!isEnabled()) return CompletableFuture.completedFuture(List.of());

        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.hvals("antidupe:fingerprints:" + fingerprint);
            } catch (Exception e) {
                System.err.println("Redis duplicate lookup failed: " + e.getMessage());
                return List.of();
            }
        });
    }

    public CompletableFuture<Void> publishItemHistory(long itemId, String action, String player, String server) {
        if (!isEnabled()) return CompletableFuture.completedFuture(null);

        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                String historyData = String.format("%d|%s|%s|%s|%d", itemId, action, player, server, System.currentTimeMillis());
                jedis.lpush("antidupe:history:" + itemId, historyData);
                jedis.ltrim("antidupe:history:" + itemId, 0, config.getInt("redis.max-history", 100));
                jedis.expire("antidupe:history:" + itemId, config.getInt("redis.history-expire", 604800));
            } catch (Exception e) {
                System.err.println("Redis history sync failed: " + e.getMessage());
            }
        });
    }

    private void subscribeToAlerts() {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.subscribe(alertSubscriber, "antidupe:alerts", "antidupe:commands");
            } catch (Exception e) {
                System.err.println("Redis subscription failed: " + e.getMessage());
            }
        });
    }

    public boolean isConnected() {
        if (!isEnabled() || jedisPool == null) return false;

        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    public void close() {
        if (alertSubscriber != null) {
            alertSubscriber.unsubscribe();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    private static class DupeAlertSubscriber extends JedisPubSub {
        private final ItemRegistry itemRegistry;

        public DupeAlertSubscriber(ItemRegistry itemRegistry) {
            this.itemRegistry = itemRegistry;
        }

        @Override
        public void onMessage(String channel, String message) {
            if ("antidupe:alerts".equals(channel)) {
                handleDupeAlert(message);
            }
        }

        private void handleDupeAlert(String message) {
            try {
                String[] parts = message.split("\\|");
                if (parts.length >= 4) {
                    String serverName = parts[0];
                    String playerName = parts[1];
                    long itemId = Long.parseLong(parts[2]);
                    int duplicateCount = Integer.parseInt(parts[3]);


                    System.out.println("[Network Alert] " + serverName + ": " + playerName +
                            " detected with " + duplicateCount + " duplicates (ID: " + itemId + ")");
                }
            } catch (Exception e) {
                System.err.println("Failed to process dupe alert: " + e.getMessage());
            }
        }
    }
}

/*
Add an SQLite and MySQL fall back (URGENT)
 */