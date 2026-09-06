package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MinionManager implements Listener {
    private final SkyblockPlugin plugin;
    private final IslandManager islands;
    private final EconomyManager economy;
    private final MilestoneManager milestones;
    private final NamespacedKey minionKey;
    private final NamespacedKey ownerKey;
    private final Map<UUID, Integer> countByOwner = new HashMap<>();
    private final int maxEntitiesPerIsland;

    public MinionManager(SkyblockPlugin plugin, IslandManager islands, EconomyManager economy, MilestoneManager milestones) {
        this.plugin = plugin;
        this.islands = islands;
        this.economy = economy;
        this.milestones = milestones;
        this.maxEntitiesPerIsland = Math.max(1, plugin.getConfig().getInt("limits.max-entities-per-island", 200));
        this.minionKey = new NamespacedKey(plugin, "minion_type");
        this.ownerKey = new NamespacedKey(plugin, "minion_owner");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public int getCount(UUID owner) {
        return countByOwner.computeIfAbsent(owner, this::countExisting);
    }

    public ItemStack createItem(String type) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();
        
        if (type.equals("collector_3x3")) {
            meta.setDisplayName("§eTier 1 Collector");
            meta.setLore(List.of("§7Automatically sells generator", "§7drops in a §a3x3 §7area.", "", "§8Place on your island"));
        } else {
            meta.setDisplayName("§6Tier 2 Collector");
            meta.setLore(List.of("§7Automatically sells generator", "§7drops in a §a5x5 §7area.", "", "§8Place on your island"));
        }
        
        meta.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    public void resetPlayer(UUID owner) {
        org.bukkit.World w = Bukkit.getWorld("skyblock");
        if (w != null) {
            for (Entity e : w.getEntitiesByClass(ArmorStand.class)) {
                if (e.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
                    if (owner.toString().equals(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                        e.remove();
                    }
                }
            }
        }
        countByOwner.remove(owner);
    }

    @EventHandler
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack hand = event.getItem();
        if (hand == null || !hand.hasItemMeta()) return;
        
        String type = hand.getItemMeta().getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
        if (type != null) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            
            Location spawnLoc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0, 0.5);
            Island island = islands.getForPlayer(player.getUniqueId()).orElse(null);
            if (island == null || !island.contains(spawnLoc, islands.radius())) {
                player.sendMessage("§cYou can only place minions on your island.");
                return;
            }
            
            if (getCount(island.owner()) >= milestones.getWorkerLimit(island.owner())) {
                player.sendMessage("§cYou have reached your Minion limit! Check /milestones to upgrade.");
                return;
            }
            long entities = spawnLoc.getWorld().getEntities().stream()
                .filter(entity -> island.contains(entity.getLocation(), islands.radius())).count();
            if (entities >= maxEntitiesPerIsland) {
                player.sendMessage("§cYour island reached its entity limit.");
                return;
            }
            
            ArmorStand as = (ArmorStand) spawnLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            as.setSmall(true);
            as.setGravity(false);
            as.setArms(true);
            as.setBasePlate(false);
            as.setCustomName(type.equals("collector_3x3") ? "§eTier 1 Collector" : "§6Tier 2 Collector");
            as.setCustomNameVisible(true);
            as.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, type);
            as.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, island.owner().toString());

            as.getEquipment().setHelmet(new ItemStack(type.equals("collector_3x3") ? Material.SKELETON_SKULL : Material.ZOMBIE_HEAD));
            as.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            as.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            as.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            
            hand.setAmount(hand.getAmount() - 1);
            countByOwner.merge(island.owner(), 1, Integer::sum);
            player.playSound(spawnLoc, Sound.ENTITY_ARMOR_STAND_PLACE, 1f, 1f);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand as) {
            if (as.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING)) {
                event.setCancelled(true); // Cancela o dano real
                
                if (event.getDamager() instanceof Player player) {
                    String ownerStr = as.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                    if (player.getUniqueId().toString().equals(ownerStr) || player.hasPermission("skyblock.admin")) {
                        String type = as.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
                        as.remove();
                        countByOwner.merge(UUID.fromString(ownerStr), -1, Integer::sum);
                        player.getInventory().addItem(createItem(type));
                        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                        player.sendMessage("§aMinion picked up!");
                    } else {
                        player.sendMessage("§cThis is not your minion!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(minionKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    public boolean autoSell(Location genLoc, long moneyValue, UUID owner) {
        Collection<Entity> nearby = genLoc.getWorld().getNearbyEntities(genLoc, 5, 5, 5, e -> e instanceof ArmorStand);
        for (Entity e : nearby) {
            if (e.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING)) {
                String type = e.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
                String ownerStr = e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
                
                if (!owner.toString().equals(ownerStr)) continue;
                
                int range = type.equals("collector_5x5") ? 2 : 1; // 2 blocos de distância = 5x5, 1 bloco = 3x3
                
                if (Math.abs(e.getLocation().getBlockX() - genLoc.getBlockX()) <= range && 
                    Math.abs(e.getLocation().getBlockZ() - genLoc.getBlockZ()) <= range) {
                    
                    economy.addBalance(owner, moneyValue);
                    milestones.addMoneyGenerated(owner, moneyValue);
                    milestones.addWorkerCollection(owner, 1);
                    
                    Player pOwner = Bukkit.getPlayer(owner);
                    if (pOwner != null && plugin.getSessionListener() != null) {
                        plugin.getSessionListener().updateScoreboard(pOwner);
                        // Pequeno aviso visual opcional
                        pOwner.sendActionBar(net.kyori.adventure.text.Component.text("§a+$" + PlayerSessionListener.formatValue(moneyValue) + " (Minion)"));
                    }
                    
                    return true;
                }
            }
        }
        return false;
    }

    private int countExisting(UUID owner) {
        int count = 0;
        org.bukkit.World w = Bukkit.getWorld("skyblock");
        if (w == null) return 0;
        for (Entity e : w.getEntitiesByClass(ArmorStand.class)) {
            if (owner.toString().equals(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING))) {
                count++;
            }
        }
        return count;
    }
}
