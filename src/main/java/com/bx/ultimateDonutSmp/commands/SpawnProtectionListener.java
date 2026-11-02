package com.bx.ultimateDonutSmp.listeners;

import com.bx.ultimateDonutSmp.UltimateDonutSmp;
import com.bx.ultimateDonutSmp.managers.CuboidManager;
import com.bx.ultimateDonutSmp.managers.SpawnManager;
import com.bx.ultimateDonutSmp.utils.PermissionUtils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Set;

/**
 * Hard spawn protection: no build, break, damage, interact, or taking damage
 * while inside any spawn cuboid/region, regardless of rank.
 * Only {@code ultimatedonutsmp.spawn.bypass} overrides this.
 */
public class SpawnProtectionListener implements Listener {

    private static final String BYPASS_PERMISSION = "ultimatedonutsmp.spawn.bypass";

    private final UltimateDonutSmp plugin;

    public SpawnProtectionListener(UltimateDonutSmp plugin) {
        this.plugin = plugin;
    }

    private boolean hasBypass(Player player) {
        return player != null && PermissionUtils.has(player, BYPASS_PERMISSION);
    }

    private boolean isInProtectedSpawn(Player player) {
        if (player == null) {
            return false;
        }
        return isInProtectedSpawn(player.getLocation());
    }

    private boolean isInProtectedSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }

        SpawnManager spawnManager = plugin.getSpawnManager();
        CuboidManager cuboidManager = plugin.getCuboidManager();
        if (spawnManager == null || cuboidManager == null) {
            return false;
        }

        Set<String> spawnCuboids = spawnManager.getAreaCuboidNames(SpawnManager.AreaType.SPAWN);
        if (spawnCuboids == null || spawnCuboids.isEmpty()) {
            // Fallback: try the primary spawn cuboid from config if present
            String legacy = plugin.getConfigManager().getConfig()
                    .getString("SHARDS.CUBOIDS.REGIONS.spawn.CUBOID", "");
            if (legacy != null && !legacy.isBlank()) {
                CuboidManager.Cuboid cuboid = cuboidManager.getCuboid(legacy.trim());
                return cuboid != null && cuboid.contains(location);
            }
            return false;
        }

        for (String name : spawnCuboids) {
            CuboidManager.Cuboid cuboid = cuboidManager.getCuboid(name);
            if (cuboid != null && cuboid.contains(location)) {
                return true;
            }
        }
        return false;
    }

    private Player getDamagerPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) {
            return;
        }
        if (isInProtectedSpawn(player) || isInProtectedSpawn(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) {
            return;
        }
        if (isInProtectedSpawn(player) || isInProtectedSpawn(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (hasBypass(player)) {
            return;
        }
        // Cancel most interactions while in spawn (buttons, doors, chests, etc. still blocked)
        if (isInProtectedSpawn(player)) {
            // Allow right-click air / nothing to not break basic movement feel, but block block interactions
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        // Prevent taking damage in spawn
        if (event.getEntity() instanceof Player victim) {
            if (!hasBypass(victim) && isInProtectedSpawn(victim)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Prevent dealing damage to others while in spawn / to entities in spawn
        Player damager = getDamagerPlayer(event.getDamager());
        if (damager != null) {
            if (!hasBypass(damager) && isInProtectedSpawn(damager)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getEntity() instanceof Player victim) {
            if (!hasBypass(victim) && isInProtectedSpawn(victim)) {
                event.setCancelled(true);
            }
        } else if (isInProtectedSpawn(event.getEntity().getLocation())) {
            // Also protect non-player entities inside spawn from being damaged by non-bypass players
            if (damager != null && !hasBypass(damager)) {
                event.setCancelled(true);
            }
        }
    }
}
