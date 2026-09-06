package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

public final class ProtectionListener implements Listener {
    private final SkyblockPlugin plugin;
    private final IslandManager islands;
    private final GeneratorManager generators;

    public ProtectionListener(SkyblockPlugin plugin, IslandManager islands, GeneratorManager generators) { 
        this.plugin = plugin;
        this.islands = islands; 
        this.generators = generators; 
    }

    private boolean deny(Player player, Block block) {
        if (!islands.isSkyblockWorld(block.getLocation()) || islands.owns(player, block.getLocation())) return false;
        player.sendMessage("§cYou cannot modify this area."); return true;
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) { event.setCancelled(true); return; }
        ItemStack generator = generators.removeGenerator(event.getBlock());
        if (generator != null) { event.setDropItems(false); event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), generator); }
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlockPlaced())) { event.setCancelled(true); return; }
        if (generators.place(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && deny(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    @EventHandler public void onBucket(PlayerBucketEmptyEvent event) { if (deny(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true); }
    @EventHandler public void onBucket(PlayerBucketFillEvent event) { if (deny(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true); }

    // Imortalidade
    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    // Sem Fome
    @EventHandler public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) {
            player.setFoodLevel(20);
        }
    }

    // Respawn Instantâneo (se morrer por kill)
    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> event.getEntity().spigot().respawn());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        islands.getForPlayer(event.getPlayer().getUniqueId()).ifPresent(island -> {
            event.setRespawnLocation(islands.home(island));
        });
    }

    // Cair no Void na ilha teleporta de volta
    @EventHandler public void onVoid(PlayerMoveEvent event) {
        if (islands.isSkyblockWorld(event.getTo()) && event.getTo().getY() < 0) {
            islands.getForPlayer(event.getPlayer().getUniqueId()).ifPresent(island -> event.getPlayer().teleport(islands.home(island)));
        }
    }
}
