package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockExpEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.world.PortalCreateEvent;

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
        return true;
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) { event.setCancelled(true); return; }
        if (generators.isGenerator(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlockPlaced())) { event.setCancelled(true); return; }
        if (generators.place(event.getPlayer(), event.getBlockPlaced(), event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        ItemStack hand = event.getItem();
        if (hand != null && (hand.getType() == org.bukkit.Material.FLINT_AND_STEEL
            || hand.getType() == org.bukkit.Material.FIRE_CHARGE
            || hand.getType().name().endsWith("_BED")
            || hand.getType() == org.bukkit.Material.RESPAWN_ANCHOR
            || hand.getType() == org.bukkit.Material.CONDUIT)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null
            && (event.getClickedBlock().getType() == org.bukkit.Material.RESPAWN_ANCHOR
                || event.getClickedBlock().getType() == org.bukkit.Material.CONDUIT)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClickedBlock() != null && deny(event.getPlayer(), event.getClickedBlock())) event.setCancelled(true);
    }

    @EventHandler
    public void onFirePlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == org.bukkit.Material.FIRE
            || event.getBlockPlaced().getType() == org.bukkit.Material.SOUL_FIRE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortalCreate(PortalCreateEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPortalTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
            && event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL
            && event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onIgnite(BlockIgniteEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        var progress = event.getPlayer().getAdvancementProgress(event.getAdvancement());
        for (String criterion : progress.getAwardedCriteria()) progress.revokeCriteria(criterion);
    }

    @EventHandler public void onBucket(PlayerBucketEmptyEvent event) { if (deny(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true); }
    @EventHandler public void onBucket(PlayerBucketFillEvent event) { if (deny(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true); }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Location location = event.getItem().getLocation();
        if (!islands.isSkyblockWorld(location)) return;
        Island island = islands.getAll().stream()
            .filter(candidate -> candidate.contains(location, islands.radius()))
            .findFirst().orElse(null);
        if (island != null && !island.hasMember(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onExpPickup(PlayerExpChangeEvent event) { event.setAmount(0); }
    @EventHandler public void onExpBottle(ExpBottleEvent event) { event.setExperience(0); event.setShowEffect(false); }
    @EventHandler public void onBlockExp(BlockExpEvent event) { event.setExpToDrop(0); }
    @EventHandler public void onDeathExp(EntityDeathEvent event) { event.setDroppedExp(0); }

    @EventHandler
    public void onBriefcaseCraft(PrepareItemCraftEvent event) {
            if (event.getInventory().getResult() == null || event.getInventory().getResult().getType() != org.bukkit.Material.IRON_INGOT) return;
            for (ItemStack item : event.getInventory().getMatrix()) {
                if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(
                        new org.bukkit.NamespacedKey(plugin, "money_value"), org.bukkit.persistence.PersistentDataType.INTEGER)) {
                    event.getInventory().setResult(null);
                    return;
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().clear();
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        event.setCancelled(true);
    }

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
