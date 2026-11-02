package com.bx.ultimateDonutSmp.managers;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.models.PlayerData;
import com.bx.ultimateDonutSmp.utils.ColorUtils;
import com.bx.ultimateDonutSmp.utils.LocationUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Manages fixed spawn holograms: welcome, store, duels, and baltop (MOST MONEY).
 * Locations are persisted under HOLOGRAMS in config.yml.
 */
public class SpawnHologramManager {

    public enum HologramType {
        WELCOME,
        STORE,
        DUELS,
        BALTOP;

        public static HologramType fromString(String input) {
            if (input == null || input.isBlank()) {
                return null;
            }
            try {
                return HologramType.valueOf(input.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private static final String TAG = "uds_spawn_hologram";
    private static final String PDC_TYPE = "spawn_hologram_type";
    private static final double DEFAULT_LINE_SPACING = 0.28D;
    private static final double DEFAULT_VIEW_RANGE = 48.0D;
    private static final long BALTOP_REFRESH_TICKS = 20L * 30L; // 30 seconds

    private final UltimateDonutSmp plugin;
    private final Map<HologramType, Location> locations = new EnumMap<>(HologramType.class);
    private final Map<HologramType, List<UUID>> entityIds = new EnumMap<>(HologramType.class);
    private BukkitTask baltopTask;

    public SpawnHologramManager(UltimateDonutSmp plugin) {
        this.plugin = plugin;
        for (HologramType type : HologramType.values()) {
            entityIds.put(type, new ArrayList<>());
        }
    }

    public void load() {
        shutdown();
        FileConfiguration config = plugin.getConfigManager().getConfig();
        for (HologramType type : HologramType.values()) {
            String path = "HOLOGRAMS." + type.name() + ".LOCATION";
            String raw = config.getString(path, "");
            Location loc = LocationUtils.parse(raw);
            if (loc != null && loc.getWorld() != null) {
                locations.put(type, loc);
            }
        }
        spawnAll();
        startBaltopTask();
    }

    public void shutdown() {
        if (baltopTask != null) {
            baltopTask.cancel();
            baltopTask = null;
        }
        clearAllEntities();
        locations.clear();
    }

    public void reload() {
        load();
    }

    public boolean setLocation(HologramType type, Location location) {
        if (type == null || location == null || location.getWorld() == null) {
            return false;
        }
        locations.put(type, location.clone());
        FileConfiguration config = plugin.getConfigManager().getConfig();
        config.set("HOLOGRAMS." + type.name() + ".LOCATION", LocationUtils.serialize(location));
        plugin.getConfigManager().saveConfig();
        respawn(type);
        return true;
    }

    public boolean clearLocation(HologramType type) {
        if (type == null) {
            return false;
        }
        locations.remove(type);
        FileConfiguration config = plugin.getConfigManager().getConfig();
        config.set("HOLOGRAMS." + type.name() + ".LOCATION", null);
        plugin.getConfigManager().saveConfig();
        removeEntities(type);
        return true;
    }

    public Location getLocation(HologramType type) {
        return locations.get(type);
    }

    private void spawnAll() {
        for (HologramType type : HologramType.values()) {
            if (locations.containsKey(type)) {
                spawn(type);
            }
        }
    }

    private void respawn(HologramType type) {
        removeEntities(type);
        spawn(type);
    }

    private void spawn(HologramType type) {
        Location base = locations.get(type);
        if (base == null || base.getWorld() == null) {
            return;
        }
        World world = base.getWorld();
        if (!world.isChunkLoaded(base.getBlockX() >> 4, base.getBlockZ() >> 4)) {
            // Will be recreated on next load/refresh when chunk is available
            return;
        }

        List<String> lines = buildLines(type, null);
        if (lines.isEmpty()) {
            return;
        }

        List<UUID> ids = new ArrayList<>();
        double spacing = DEFAULT_LINE_SPACING;
        for (int i = 0; i < lines.size(); i++) {
            Location lineLoc = base.clone().add(0, -i * spacing, 0);
            final String line = lines.get(i);
            TextDisplay display = world.spawn(lineLoc, TextDisplay.class, td -> {
                td.setText(ColorUtils.toComponent(line));
                configure(td);
                td.addScoreboardTag(TAG);
                td.getPersistentDataContainer().set(
                        plugin.getKey(PDC_TYPE),
                        PersistentDataType.STRING,
                        type.name()
                );
            });
            ids.add(display.getUniqueId());
        }
        entityIds.put(type, ids);
    }

    private void configure(TextDisplay td) {
        td.setBillboard(Display.Billboard.CENTER);
        td.setDefaultBackground(false);
        td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        td.setSeeThrough(true);
        td.setShadowed(false);
        td.setViewRange((float) (DEFAULT_VIEW_RANGE / 64.0D));
        td.setPersistent(false);
    }

    private List<String> buildLines(HologramType type, Player viewer) {
        return switch (type) {
            case WELCOME -> buildWelcomeLines(viewer);
            case STORE -> buildStoreLines();
            case DUELS -> buildDuelsLines();
            case BALTOP -> buildBaltopLines(viewer);
        };
    }

    private List<String> buildWelcomeLines(Player viewer) {
        // Match screenshot style; domain updated to ogdonutmc.net per request.
        // Global TextDisplays cannot show a unique name per viewer; use a friendly generic line.
        // (Personal welcome name appears in chat/join messages elsewhere.)
        return List.of(
                "&b&logdonutmc.net",
                "&fWelcome!",
                "&fType &a/rtp &fto get started!",
                "&fType &a/sell &fto start making money!"
        );
    }

    private List<String> buildStoreLines() {
        return List.of(
                "&f&lSTORE INFORMATION",
                "&7All crate keys are obtainable through keyalls",
                "&7All items in crates are easily obtainable for",
                "&7free through normal gameplay means",
                "",
                "&7Please only buy keys if you can",
                "&7afford it and want to support the server"
        );
    }

    private List<String> buildDuelsLines() {
        return List.of(
                "&c&lDUELS",
                "&cFight other players!",
                "",
                "&7Warning:",
                "&7No Keep Inventory",
                "",
                "&8(click to queue)"
        );
    }

    private List<String> buildBaltopLines(Player viewer) {
        List<String> lines = new ArrayList<>();
        lines.add("&b&lMOST MONEY");

        LeaderboardManager lb = plugin.getLeaderboardManager();
        if (lb == null) {
            lines.add("&7Leaderboard unavailable");
            return lines;
        }

        List<LeaderboardManager.LeaderboardEntry> top = lb.getEntries(LeaderboardManager.LeaderboardType.MONEY, 0, 10);
        for (LeaderboardManager.LeaderboardEntry entry : top) {
            PlayerData data = entry.playerData();
            String name = data.getUsername() != null ? data.getUsername() : "Unknown";
            String value = lb.formatValue(LeaderboardManager.LeaderboardType.MONEY, data, true, false);
            // Style similar to screenshot: #1 Name: 8.01B
            lines.add("&b#" + entry.position() + " &f" + name + "&7: &b" + value);
        }

        if (viewer != null) {
            LeaderboardManager.LeaderboardEntry self = lb.getPlayerEntry(viewer.getUniqueId(), LeaderboardManager.LeaderboardType.MONEY);
            if (self != null) {
                lines.add("");
                String name = self.playerData().getUsername() != null ? self.playerData().getUsername() : viewer.getName();
                String value = lb.formatValue(LeaderboardManager.LeaderboardType.MONEY, self.playerData(), true, false);
                lines.add("&b#" + self.position() + " &f." + name + "&7: &b" + value);
            }
        }
        return lines;
    }

    private void startBaltopTask() {
        if (baltopTask != null) {
            baltopTask.cancel();
        }
        baltopTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (locations.containsKey(HologramType.BALTOP)) {
                // Global lines only (viewer-specific rank requires personal displays)
                respawn(HologramType.BALTOP);
            }
        }, BALTOP_REFRESH_TICKS, BALTOP_REFRESH_TICKS);
    }

    private void removeEntities(HologramType type) {
        List<UUID> ids = entityIds.getOrDefault(type, List.of());
        for (UUID id : ids) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        entityIds.put(type, new ArrayList<>());
    }

    private void clearAllEntities() {
        for (HologramType type : HologramType.values()) {
            removeEntities(type);
        }
        // Also purge any leftover tagged displays
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }

    /**
     * Optional: when a player approaches the welcome hologram, refresh with their name.
     * Called from a lightweight move/task if desired; currently welcome uses generic name unless set with viewer.
     */
    public void refreshWelcomeFor(Player player) {
        if (player == null || !locations.containsKey(HologramType.WELCOME)) {
            return;
        }
        // For a true personal welcome, we'd need per-player displays.
        // Global welcome shows domain + instructions; name is best-effort at set time.
    }
}
