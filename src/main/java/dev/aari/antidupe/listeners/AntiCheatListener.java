package dev.aari.antidupe.listeners;

import dev.aari.antidupe.AntiDupe;
import dev.aari.antidupe.config.ConfigManager;
import dev.aari.antidupe.managers.DupeDebugManager;
import dev.aari.antidupe.util.SoundUtil;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public final class AntiCheatListener implements Listener {

    private static final int MAX_CPS = 15;
    private static final long CPS_WINDOW = 1000L;
    private static final long CRYSTAL_THRESHOLD = 200L;
    private static final long GHOST_BLOCK_THRESHOLD = 500L;
    private static final int MAX_VIOLATIONS = 5;
    private static final int MAX_CRYSTAL_VIOLATIONS = 3;
    private static final double MAX_REACH = 6.0;

    private final AntiDupe plugin;
    private final ConfigManager config;
    private final DupeDebugManager debugManager;

    private final Object2IntOpenHashMap<UUID> clickCounts;
    private final Object2LongOpenHashMap<UUID> lastClickTime;
    private final Object2LongOpenHashMap<UUID> lastMoveTime;
    private final Object2LongOpenHashMap<UUID> lastCrystalInteract;
    private final Object2LongOpenHashMap<UUID> lastBlockPlace;
    private final Object2LongOpenHashMap<UUID> lastEntityAttack;
    private final Object2IntOpenHashMap<UUID> violations;
    private final Object2IntOpenHashMap<UUID> crystalViolations;
    private final Object2LongOpenHashMap<UUID> lastGhostCheck;

    public AntiCheatListener(AntiDupe plugin, ConfigManager config, DupeDebugManager debugManager) {
        this.plugin = plugin;
        this.config = config;
        this.debugManager = debugManager;

        this.clickCounts = new Object2IntOpenHashMap<>(64);
        this.lastClickTime = new Object2LongOpenHashMap<>(64);
        this.lastMoveTime = new Object2LongOpenHashMap<>(64);
        this.lastCrystalInteract = new Object2LongOpenHashMap<>(32);
        this.lastBlockPlace = new Object2LongOpenHashMap<>(32);
        this.lastEntityAttack = new Object2LongOpenHashMap<>(32);
        this.violations = new Object2IntOpenHashMap<>(32);
        this.crystalViolations = new Object2IntOpenHashMap<>(16);
        this.lastGhostCheck = new Object2LongOpenHashMap<>(32);

        this.clickCounts.defaultReturnValue(0);
        this.lastClickTime.defaultReturnValue(0L);
        this.lastMoveTime.defaultReturnValue(0L);
        this.lastCrystalInteract.defaultReturnValue(0L);
        this.lastBlockPlace.defaultReturnValue(0L);
        this.lastEntityAttack.defaultReturnValue(0L);
        this.violations.defaultReturnValue(0);
        this.crystalViolations.defaultReturnValue(0);
        this.lastGhostCheck.defaultReturnValue(0L);

        startCleanupTask();
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.END_CRYSTAL) return;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastInteract = lastCrystalInteract.getLong(playerId);

        if (currentTime - lastInteract < CRYSTAL_THRESHOLD) {
            final int violations = crystalViolations.getInt(playerId) + 1;
            crystalViolations.put(playerId, violations);

            if (violations >= MAX_CRYSTAL_VIOLATIONS) {
                event.setCancelled(true);
                recordViolation(player, "CRYSTAL_AURA");
                item.setAmount(item.getAmount() - 1);
                crystalViolations.put(playerId, 0);
                SoundUtil.playErrorSound(player);
                SoundUtil.sendActionBar(player, config.getMessage("crystal-aura-detected"));
            }
        }

        lastCrystalInteract.put(playerId, currentTime);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockPlace(BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final Block block = event.getBlock();

        if (block.getType() != Material.END_CRYSTAL) return;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastPlace = lastBlockPlace.getLong(playerId);

        if (currentTime - lastPlace < CRYSTAL_THRESHOLD) {
            event.setCancelled(true);
            recordViolation(player, "RAPID_CRYSTAL_PLACE");
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("crystal-aura-detected"));
        }

        lastBlockPlace.put(playerId, currentTime);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (event.getEntityType() != EntityType.END_CRYSTAL) return;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastAttack = lastEntityAttack.getLong(playerId);
        final long lastPlace = lastBlockPlace.getLong(playerId);

        if (currentTime - lastPlace < 100L) {
            event.setCancelled(true);
            recordViolation(player, "CRYSTAL_AURA_COMBO");
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("crystal-aura-detected"));
            return;
        }

        if (currentTime - lastAttack < 150L) {
            final int violations = crystalViolations.getInt(playerId) + 1;
            crystalViolations.put(playerId, violations);

            if (violations >= 2) {
                event.setCancelled(true);
                recordViolation(player, "RAPID_CRYSTAL_ATTACK");
                crystalViolations.put(playerId, 0);
                SoundUtil.playErrorSound(player);
                SoundUtil.sendActionBar(player, config.getMessage("crystal-aura-detected"));
            }
        }

        final double distance = player.getLocation().distance(event.getEntity().getLocation());
        if (distance > MAX_REACH) {
            event.setCancelled(true);
            recordViolation(player, "CRYSTAL_REACH");
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("impossible-reach"));
        }

        lastEntityAttack.put(playerId, currentTime);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final ItemStack cursor = event.getCursor();
        final ItemStack current = event.getCurrentItem();

        if (cursor != null && !cursor.getType().isAir() &&
                current != null && !current.getType().isAir() &&
                cursor.isSimilar(current)) {

            event.setCancelled(true);
            event.setCursor(null);
            event.setCurrentItem(current);

            recordViolation(player, "CREATIVE_DUPE_ATTEMPT");
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("creative-dupe-blocked"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastClick = lastClickTime.getLong(playerId);

        if (currentTime - lastClick < CPS_WINDOW) {
            final int clicks = clickCounts.getInt(playerId) + 1;
            clickCounts.put(playerId, clicks);

            if (clicks >= MAX_CPS) {
                recordViolation(player, "HIGH_CPS");
                clickCounts.put(playerId, 0);
                debugManager.broadcastDupeAlert(player.getName(), -1L, clicks);
                SoundUtil.playErrorSound(player);
                SoundUtil.sendActionBar(player, config.getMessage("high-cps-detected", "cps", clicks));
            }
        } else {
            clickCounts.put(playerId, 1);
        }

        lastClickTime.put(playerId, currentTime);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        final Player player = event.getPlayer();
        final UUID playerId = player.getUniqueId();
        final long currentTime = System.currentTimeMillis();
        final long lastMove = lastMoveTime.getLong(playerId);

        if (currentTime - lastMove < 100L) return;
        lastMoveTime.put(playerId, currentTime);

        checkGhostBlocks(player, currentTime);
        analyzeSuspiciousMovement(player, event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        final Player player = event.getPlayer();

        if (player.hasPermission("antidupe.bypass.flight")) return;
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight()) return;

        if (event.isFlying()) {
            event.setCancelled(true);
            recordViolation(player, "UNAUTHORIZED_FLIGHT");
            debugManager.broadcastDupeAlert(player.getName(), -1L, 1);
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("flight-not-allowed"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        clickCounts.removeInt(playerId);
        lastClickTime.removeLong(playerId);
        lastMoveTime.removeLong(playerId);
        lastCrystalInteract.removeLong(playerId);
        lastBlockPlace.removeLong(playerId);
        lastEntityAttack.removeLong(playerId);
        violations.removeInt(playerId);
        crystalViolations.removeInt(playerId);
        lastGhostCheck.removeLong(playerId);
    }

    private void checkGhostBlocks(Player player, long currentTime) {
        final UUID playerId = player.getUniqueId();
        final long lastCheck = lastGhostCheck.getLong(playerId);

        if (currentTime - lastCheck < GHOST_BLOCK_THRESHOLD) return;
        lastGhostCheck.put(playerId, currentTime);

        final Location loc = player.getLocation();
        final Block block = loc.getBlock();

        if (block.getType().isSolid() && !isPassableBlock(block.getType())) {
            recordViolation(player, "GHOST_BLOCK");

            final Location safeLoc = findSafeLocation(player.getLocation());
            if (safeLoc != null) {
                player.teleport(safeLoc);
            }

            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("ghost-block-detected"));
        }
    }

    private void analyzeSuspiciousMovement(Player player, Location from, Location to) {
        if (from == null || to == null) return;

        final double distance = from.distance(to);
        final double yChange = Math.abs(to.getY() - from.getY());

        if (distance > 10.0 && player.getGameMode() != GameMode.CREATIVE) {
            recordViolation(player, "IMPOSSIBLE_SPEED");
            SoundUtil.playErrorSound(player);
            SoundUtil.sendActionBar(player, config.getMessage("suspicious-movement"));
        }

        if (yChange > 5.0 && player.getGameMode() != GameMode.CREATIVE && !player.isFlying()) {
            recordViolation(player, "SUSPICIOUS_VERTICAL_MOVEMENT");
        }
    }

    private boolean isPassableBlock(Material material) {
        return material == Material.AIR ||
                material == Material.WATER ||
                material == Material.LAVA ||
                material.name().contains("DOOR") ||
                material.name().contains("SIGN") ||
                material.name().contains("BANNER");
    }

    private Location findSafeLocation(Location original) {
        for (int y = original.getBlockY(); y < 256; y++) {
            final Location testLoc = original.clone();
            testLoc.setY(y);

            if (testLoc.getBlock().getType() == Material.AIR &&
                    testLoc.clone().add(0, 1, 0).getBlock().getType() == Material.AIR) {
                return testLoc;
            }
        }
        return original.getWorld().getSpawnLocation();
    }

    private void recordViolation(Player player, String type) {
        final UUID playerId = player.getUniqueId();
        final int currentViolations = violations.getInt(playerId) + 1;
        violations.put(playerId, currentViolations);

        plugin.getSLF4JLogger().warn("AntiCheat: {} - {} (Violation #{}/{})",
                player.getName(), type, currentViolations, MAX_VIOLATIONS);

        if (currentViolations >= MAX_VIOLATIONS) {
            debugManager.broadcastDupeAlert(player.getName(), -1L, currentViolations);
            violations.put(playerId, 0);

            final String punishment = config.getString("anticheat.punishment-command", "");
            if (!punishment.isEmpty()) {
                final String command = punishment.replace("{player}", player.getName()).replace("{type}", type);
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            }
        }
    }

    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                final long threshold = System.currentTimeMillis() - 300_000L;

                lastClickTime.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                lastMoveTime.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                lastCrystalInteract.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                lastBlockPlace.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                lastEntityAttack.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);
                lastGhostCheck.object2LongEntrySet().removeIf(entry -> entry.getLongValue() < threshold);

                clickCounts.keySet().removeIf(key -> !lastClickTime.containsKey(key));
                violations.keySet().removeIf(key -> !lastClickTime.containsKey(key) && !lastMoveTime.containsKey(key));
                crystalViolations.keySet().removeIf(key -> !lastCrystalInteract.containsKey(key));
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);
    }

    public void cleanup() {
        clickCounts.clear();
        lastClickTime.clear();
        lastMoveTime.clear();
        lastCrystalInteract.clear();
        lastBlockPlace.clear();
        lastEntityAttack.clear();
        violations.clear();
        crystalViolations.clear();
        lastGhostCheck.clear();
    }
}