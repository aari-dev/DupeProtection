package dev.aari.antidupe.data;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ItemRegistry {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis() << 20);

    private final AntiDupe plugin;
    private final Path dataFile;
    private final Long2ObjectMap<TrackedItem> itemDatabase;
    private final Object2LongMap<String> itemTypeCount;
    private final ConcurrentHashMap<Long, List<ItemAction>> itemHistory;
    private volatile AsynchronousFileChannel fileChannel;

    public ItemRegistry(AntiDupe plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("items.dat");
        this.itemDatabase = new Long2ObjectOpenHashMap<>(16384, 0.75f);
        this.itemTypeCount = new Object2LongOpenHashMap<>(256);
        this.itemHistory = new ConcurrentHashMap<>(4096, 0.75f, 16);
        this.itemTypeCount.defaultReturnValue(0L);
    }

    public void initialize() {
        try {
            Files.createDirectories(dataFile.getParent());
            this.fileChannel = AsynchronousFileChannel.open(dataFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
            loadFromDisk();
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize ItemRegistry", e);
        }
    }

    public long registerItem(ItemStack item, String action, String player) {
        if (item == null || item.getType().isAir()) return -1L;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Long existingId = pdc.get(ItemIdentifier.ITEM_ID_KEY, PersistentDataType.LONG);

        if (existingId != null) {
            addItemAction(existingId, action, player);
            checkForDuplicatesAsync(existingId, player, action);
            return existingId;
        }

        long newId = generateUniqueId();
        String fingerprint = ItemIdentifier.createFingerprint(item);
        TrackedItem tracked = new TrackedItem(newId, fingerprint, System.currentTimeMillis(), player);

        synchronized (itemDatabase) {
            itemDatabase.put(newId, tracked);
            String typeKey = item.getType().name();
            itemTypeCount.put(typeKey, itemTypeCount.getLong(typeKey) + 1);
        }

        addItemAction(newId, action, player);
        ItemIdentifier.markItem(item, newId);
        persistToDisk();

        checkForDuplicatesAsync(newId, player, action);

        return newId;
    }

    private void checkForDuplicatesAsync(long itemId, String player, String action) {
        if (!plugin.getConfigManager().getBoolean("settings.broadcast-alerts", true)) {
            return;
        }

        List<String> ignoredActions = plugin.getConfigManager().getConfig()
                .getStringList("settings.ignored-alert-actions");
        if (ignoredActions.contains(action)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            List<TrackedItem> duplicates = findDuplicates(itemId);
            int minDuplicates = plugin.getConfigManager().getInt("settings.min-duplicates-for-alert", 1);

            if (duplicates.size() >= minDuplicates) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (plugin.getDupeDebugManager() != null) {
                        plugin.getDupeDebugManager().broadcastDupeAlert(player, itemId, duplicates.size());
                    }
                });
            }
        });
    }

    public List<TrackedItem> findDuplicates(long itemId) {
        TrackedItem target = itemDatabase.get(itemId);
        if (target == null) return List.of();

        List<TrackedItem> duplicates = new ArrayList<>(16);
        String targetFingerprint = target.fingerprint();

        synchronized (itemDatabase) {
            for (TrackedItem item : itemDatabase.values()) {
                if (item.id() != itemId && targetFingerprint.equals(item.fingerprint())) {
                    duplicates.add(item);
                }
            }
        }

        return duplicates;
    }

    public TrackedItem getItem(long id) {
        return itemDatabase.get(id);
    }

    public List<ItemAction> getItemHistory(long id) {
        return itemHistory.getOrDefault(id, List.of());
    }

    public Object2LongMap<String> getItemTypeStatistics() {
        synchronized (itemDatabase) {
            return new Object2LongOpenHashMap<>(itemTypeCount);
        }
    }

    private void addItemAction(long itemId, String action, String player) {
        itemHistory.computeIfAbsent(itemId, k -> new ArrayList<>(8))
                .add(new ItemAction(System.currentTimeMillis(), action, player));
    }

    private long generateUniqueId() {
        long timestamp = System.currentTimeMillis();
        long randomPart = RANDOM.nextLong() & 0xFFFFF;
        return (timestamp << 20) | randomPart;
    }

    private void loadFromDisk() {
        if (!Files.exists(dataFile)) return;

        CompletableFuture.runAsync(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.allocate((int) Files.size(dataFile));
                fileChannel.read(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        if (result > 0) {
                            attachment.flip();
                            deserializeData(attachment);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        plugin.getSLF4JLogger().error("Failed to load item data", exc);
                    }
                });
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Error reading item data file", e);
            }
        });
    }

    private void persistToDisk() {
        if (fileChannel == null) return;

        CompletableFuture.runAsync(() -> {
            ByteBuffer buffer = serializeData();
            fileChannel.write(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
                @Override
                public void completed(Integer result, ByteBuffer attachment) {
                    // Data persisted successfully
                }

                @Override
                public void failed(Throwable exc, ByteBuffer attachment) {
                    plugin.getSLF4JLogger().error("Failed to persist item data", exc);
                }
            });
        });
    }

    private ByteBuffer serializeData() {
        synchronized (itemDatabase) {
            ByteBuffer buffer = ByteBuffer.allocate(itemDatabase.size() * 128);
            buffer.putInt(itemDatabase.size());

            for (TrackedItem item : itemDatabase.values()) {
                buffer.putLong(item.id());
                byte[] fingerprint = item.fingerprint().getBytes();
                buffer.putInt(fingerprint.length);
                buffer.put(fingerprint);
                buffer.putLong(item.timestamp());
                byte[] creator = item.creator().getBytes();
                buffer.putInt(creator.length);
                buffer.put(creator);
            }

            buffer.flip();
            return buffer;
        }
    }

    private void deserializeData(ByteBuffer buffer) {
        int count = buffer.getInt();

        synchronized (itemDatabase) {
            for (int i = 0; i < count; i++) {
                long id = buffer.getLong();

                int fingerprintLen = buffer.getInt();
                byte[] fingerprintBytes = new byte[fingerprintLen];
                buffer.get(fingerprintBytes);
                String fingerprint = new String(fingerprintBytes);

                long timestamp = buffer.getLong();

                int creatorLen = buffer.getInt();
                byte[] creatorBytes = new byte[creatorLen];
                buffer.get(creatorBytes);
                String creator = new String(creatorBytes);

                TrackedItem item = new TrackedItem(id, fingerprint, timestamp, creator);
                itemDatabase.put(id, item);
            }
        }

        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.getSLF4JLogger().info("Loaded {} tracked items from disk", count));
    }

    public void shutdown() {
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                persistToDisk();
                fileChannel.close();
            }
        } catch (IOException e) {
            plugin.getSLF4JLogger().error("Error during shutdown", e);
        }
    }

    public record TrackedItem(long id, String fingerprint, long timestamp, String creator) {}

    public record ItemAction(long timestamp, String action, String player) {}
}