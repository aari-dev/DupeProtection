package dev.aari.antidupe.data;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.util.ItemIdentifier;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public final class ItemRegistry {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis() << 20);
    private static final int INITIAL_CAPACITY = 8192;
    private static final float LOAD_FACTOR = 0.75f;

    private final AntiDupe plugin;
    private final Path dataFile;
    private final Long2ObjectOpenHashMap<TrackedItem> itemDatabase;
    private final Object2LongOpenHashMap<String> itemTypeCount;
    private volatile AsynchronousFileChannel fileChannel;
    private volatile boolean initialized = false;

    public ItemRegistry(AntiDupe plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("items.dat");
        this.itemDatabase = new Long2ObjectOpenHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR);
        this.itemTypeCount = new Object2LongOpenHashMap<>(256);
        this.itemTypeCount.defaultReturnValue(0L);

        startMaintenanceTask();
    }

    public void initialize() {
        try {
            Files.createDirectories(dataFile.getParent());
            this.fileChannel = AsynchronousFileChannel.open(dataFile,
                    StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);

            if (Files.exists(dataFile) && Files.size(dataFile) > 0) {
                loadFromDiskAsync();
            }

            this.initialized = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize ItemRegistry", e);
        }
    }

    public long registerItem(ItemStack item, String action, String player) {
        if (!initialized || item == null || item.getType().isAir()) return -1L;

        final PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        final Long existingId = pdc.get(ItemIdentifier.ITEM_ID_KEY, PersistentDataType.LONG);

        if (existingId != null) {
            checkForDuplicatesAsync(existingId, player, action);
            return existingId;
        }

        final long newId = generateId();
        final String fingerprint = ItemIdentifier.createFingerprint(item);
        final TrackedItem tracked = new TrackedItem(newId, fingerprint, System.currentTimeMillis(), player);

        synchronized (itemDatabase) {
            itemDatabase.put(newId, tracked);
            incrementTypeCount(item.getType().name());
        }

        ItemIdentifier.markItem(item, newId);
        scheduleAsyncSave();
        checkForDuplicatesAsync(newId, player, action);

        return newId;
    }

    public List<TrackedItem> findDuplicates(long itemId) {
        if (!initialized) return List.of();

        final TrackedItem target;
        synchronized (itemDatabase) {
            target = itemDatabase.get(itemId);
        }

        if (target == null) return List.of();

        final String targetFingerprint = target.fingerprint();
        final List<TrackedItem> duplicates = new ArrayList<>(4);

        synchronized (itemDatabase) {
            for (final TrackedItem item : itemDatabase.values()) {
                if (item.id() != itemId && targetFingerprint.equals(item.fingerprint())) {
                    duplicates.add(item);
                    if (duplicates.size() >= 100) break; // Limit results for performance
                }
            }
        }

        return duplicates;
    }

    public TrackedItem getItem(long id) {
        synchronized (itemDatabase) {
            return itemDatabase.get(id);
        }
    }

    public List<ItemAction> getItemHistory(long id) {
        return List.of(new ItemAction(System.currentTimeMillis(), "TRACKED", "SYSTEM"));
    }

    public Object2LongOpenHashMap<String> getItemTypeStatistics() {
        synchronized (itemDatabase) {
            return itemTypeCount.clone();
        }
    }

    private long generateId() {
        return ID_GENERATOR.getAndIncrement() | (ThreadLocalRandom.current().nextLong() & 0xFFFFL);
    }

    private void incrementTypeCount(String type) {
        itemTypeCount.put(type, itemTypeCount.getLong(type) + 1L);
    }

    private void checkForDuplicatesAsync(long itemId, String player, String action) {
        if (!shouldCheckForDuplicates(action)) return;

        CompletableFuture.supplyAsync(() -> findDuplicates(itemId))
                .thenAccept(duplicates -> {
                    if (!duplicates.isEmpty() && plugin.getDupeDebugManager() != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                plugin.getDupeDebugManager().broadcastDupeAlert(player, itemId, duplicates.size())
                        );
                    }
                });
    }

    private boolean shouldCheckForDuplicates(String action) {
        return !"LOGIN_SCAN".equals(action) && !"DEBUG_SCAN".equals(action) && !"MOVED".equals(action);
    }

    private void loadFromDiskAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                final ByteBuffer buffer = ByteBuffer.allocate((int) Files.size(dataFile));
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
                        plugin.getSLF4JLogger().error("Failed to load data", exc);
                    }
                });
            } catch (IOException e) {
                plugin.getSLF4JLogger().error("Error reading data file", e);
            }
        });
    }

    private void scheduleAsyncSave() {
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, this::persistToDisk, 100L);
    }

    private void persistToDisk() {
        if (fileChannel == null) return;

        final ByteBuffer buffer = serializeData();
        fileChannel.write(buffer, 0, buffer, new CompletionHandler<Integer, ByteBuffer>() {
            @Override
            public void completed(Integer result, ByteBuffer attachment) {
                // Success - no action needed
            }

            @Override
            public void failed(Throwable exc, ByteBuffer attachment) {
                plugin.getSLF4JLogger().error("Failed to save data", exc);
            }
        });
    }

    private ByteBuffer serializeData() {
        synchronized (itemDatabase) {
            final ByteBuffer buffer = ByteBuffer.allocate(itemDatabase.size() * 64 + 4);
            buffer.putInt(itemDatabase.size());

            for (final TrackedItem item : itemDatabase.values()) {
                buffer.putLong(item.id());
                writeString(buffer, item.fingerprint());
                buffer.putLong(item.timestamp());
                writeString(buffer, item.creator());
            }

            buffer.flip();
            return buffer;
        }
    }

    private void deserializeData(ByteBuffer buffer) {
        final int count = buffer.getInt();

        synchronized (itemDatabase) {
            for (int i = 0; i < count && buffer.hasRemaining(); i++) {
                final long id = buffer.getLong();
                final String fingerprint = readString(buffer);
                final long timestamp = buffer.getLong();
                final String creator = readString(buffer);

                itemDatabase.put(id, new TrackedItem(id, fingerprint, timestamp, creator));
            }
        }

        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.getSLF4JLogger().info("Loaded {} tracked items", count));
    }

    private void writeString(ByteBuffer buffer, String str) {
        final byte[] bytes = str.getBytes();
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }

    private String readString(ByteBuffer buffer) {
        final int length = buffer.getInt();
        final byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes);
    }

    private void startMaintenanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                synchronized (itemDatabase) {
                    if (itemDatabase.size() > 50_000) {
                        final long cutoff = System.currentTimeMillis() - 604_800_000L; // 7 days
                        itemDatabase.values().removeIf(item -> item.timestamp() < cutoff);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 72000L, 72000L); // Every hour
    }

    public void shutdown() {
        try {
            if (fileChannel != null && fileChannel.isOpen()) {
                persistToDisk();
                Thread.sleep(100); // Allow save to complete
                fileChannel.close();
            }
        } catch (IOException | InterruptedException e) {
            plugin.getSLF4JLogger().error("Error during shutdown", e);
        }
    }

    public record TrackedItem(long id, String fingerprint, long timestamp, String creator) {}
    public record ItemAction(long timestamp, String action, String player) {}
}