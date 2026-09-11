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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import java.util.Base64;

public class MinionManager implements Listener {
    private final SkyblockPlugin plugin;
    private final IslandManager islands;
    private final EconomyManager economy;
    private final MilestoneManager milestones;
    private final NamespacedKey minionKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey moneyKey;
    private final NamespacedKey amountKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey progressKey;
    private final NamespacedKey generatedKey;
    private final NamespacedKey upgradeKey;
    private final NamespacedKey inventoryKey;
    private final NamespacedKey selectedOreKey;
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
        this.moneyKey = new NamespacedKey(plugin, "money_value");
        this.amountKey = new NamespacedKey(plugin, "money_amount");
        this.levelKey = new NamespacedKey(plugin, "worker_level");
        this.progressKey = new NamespacedKey(plugin, "worker_progress");
        this.generatedKey = new NamespacedKey(plugin, "worker_generated");
        this.upgradeKey = new NamespacedKey(plugin, "worker_upgrades");
        this.inventoryKey = new NamespacedKey(plugin, "collector_inventory");
        this.selectedOreKey = new NamespacedKey(plugin, "selected_ore");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::runMiners, 100L, 100L);
    }

    private boolean isMinion(ArmorStand stand) {
        return stand.getPersistentDataContainer().has(minionKey, PersistentDataType.STRING);
    }

    private boolean canUse(Player player, ArmorStand stand) {
        String owner = stand.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return player.hasPermission("skyblock.admin") || player.getUniqueId().toString().equals(owner);
    }

    private void openInventory(Player player, ArmorStand stand, boolean upgrades) {
        if (!canUse(player, stand)) {
            player.sendMessage("§cEste coletor não pertence a você.");
            return;
        }
        String type = stand.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
        String title = upgrades ? "§8" + (type.equals("miner") ? "Miner" : "Collector") + " Upgrades"
            : "§8" + (type.equals("miner") ? "Miner" : "Collector") + " Inventory";
        Inventory inventory = Bukkit.createInventory(new CollectorHolder(stand.getUniqueId(), upgrades), 27, title);
        if (upgrades) {
            ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta glassMeta = glass.getItemMeta();
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, glass);
            for (int slot : new int[] {11, 13, 15}) inventory.setItem(slot, null);
            if ("miner".equals(type)) inventory.setItem(4, createOreSelector(stand));
            inventory.setItem(22, createStatusItem(stand));
        } else {
            loadInventory(stand, inventory);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onCollectorInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof ArmorStand stand) || !isMinion(stand)) return;
        event.setCancelled(true);
        openInventory(event.getPlayer(), stand, event.getPlayer().isSneaking());
    }

    @EventHandler
    public void onCollectorClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CollectorHolder holder) || holder.upgrades()) return;
        Entity entity = Bukkit.getEntity(holder.id());
        if (entity instanceof ArmorStand stand && isMinion(stand)) saveInventory(stand, event.getInventory());
    }

    @EventHandler
    public void onUpgradeClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CollectorHolder holder) || !holder.upgrades()) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 4) return;
        Entity entity = Bukkit.getEntity(holder.id());
        if (!(entity instanceof ArmorStand miner) || !"miner".equals(miner.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING))) return;
        List<Material> unlocked = unlockedOres(workerLevel(miner));
        Material current = selectedOre(miner);
        int next = (unlocked.indexOf(current) + 1) % unlocked.size();
        miner.getPersistentDataContainer().set(selectedOreKey, PersistentDataType.STRING, unlocked.get(next).name());
        event.getInventory().setItem(4, createOreSelector(miner));
    }

    @EventHandler
    public void onUpgradeDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof CollectorHolder holder && holder.upgrades()) {
            event.setCancelled(true);
        }
    }

    public int getCount(UUID owner) {
        return countByOwner.computeIfAbsent(owner, this::countExisting);
    }

    public ItemStack createItem(String type) {
        return createItem(type, null);
    }

    private ItemStack createItem(String type, ArmorStand worker) {
        ItemStack item = new ItemStack(Material.ARMOR_STAND);
        ItemMeta meta = item.getItemMeta();
        int level = worker == null ? 1 : workerLevel(worker);
        int progress = worker == null ? 0 : workerProgress(worker);
        long amount = worker == null ? 0 : worker.getPersistentDataContainer()
            .getOrDefault(generatedKey, PersistentDataType.LONG, 0L);
        String upgrades = worker == null ? "None" : worker.getPersistentDataContainer()
            .getOrDefault(upgradeKey, PersistentDataType.STRING, "None");
        if (type.equals("miner")) {
            meta.setDisplayName("§bMiner");
            meta.setLore(List.of("§7Creates: §f" + (worker == null ? "Coal Ore" : selectedOreName(worker)), "§7Level: §f" + level + " §7(" + nextLevelRequirement("miner", level) + " required)",
                "§7Ores generated: §f" + amount, "§7Upgrades: §f" + upgrades, "", "§8Place on your island"));
        } else {
            meta.setDisplayName("§eCollector");
            meta.setLore(List.of("§7Collection range: §f" + (1 + ((level - 1) / 5)) + " blocks", "§7Level: §f" + level + " §7(" + nextLevelRequirement("collector", level) + " required)",
                "§7Items collected: §f" + amount, "§7Upgrades: §f" + upgrades, "", "§8Place on your island"));
        }
        
        meta.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, type);
        if (worker != null) {
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
            meta.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, progress);
            meta.getPersistentDataContainer().set(generatedKey, PersistentDataType.LONG, amount);
            meta.getPersistentDataContainer().set(upgradeKey, PersistentDataType.STRING, upgrades);
            String selectedOre = worker.getPersistentDataContainer().get(selectedOreKey, PersistentDataType.STRING);
            if (selectedOre != null) meta.getPersistentDataContainer().set(selectedOreKey, PersistentDataType.STRING, selectedOre);
            String inventory = worker.getPersistentDataContainer().get(inventoryKey, PersistentDataType.STRING);
            if (inventory != null) meta.getPersistentDataContainer().set(inventoryKey, PersistentDataType.STRING, inventory);
        }
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
            if (type.equals("collector_3x3") || type.equals("collector_5x5")) type = "collector";
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
            as.setCustomName(type.equals("miner") ? "§bMiner" : "§eCollector");
            as.setCustomNameVisible(false);
            as.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, type);
            as.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, island.owner().toString());
            as.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, 1);
            as.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER, 0);
            ItemMeta handMeta = hand.getItemMeta();
            var handPdc = handMeta.getPersistentDataContainer();
            as.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER,
                handPdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
            as.getPersistentDataContainer().set(progressKey, PersistentDataType.INTEGER,
                handPdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0));
            as.getPersistentDataContainer().set(generatedKey, PersistentDataType.LONG,
                handPdc.getOrDefault(generatedKey, PersistentDataType.LONG, 0L));
            String upgrades = handPdc.get(upgradeKey, PersistentDataType.STRING);
            if (upgrades != null) as.getPersistentDataContainer().set(upgradeKey, PersistentDataType.STRING, upgrades);
            String inventory = handPdc.get(inventoryKey, PersistentDataType.STRING);
            if (inventory != null) as.getPersistentDataContainer().set(inventoryKey, PersistentDataType.STRING, inventory);
            String selectedOre = handPdc.get(selectedOreKey, PersistentDataType.STRING);
            if (selectedOre != null) as.getPersistentDataContainer().set(selectedOreKey, PersistentDataType.STRING, selectedOre);

            as.getEquipment().setHelmet(new ItemStack(type.equals("miner") ? Material.PLAYER_HEAD : Material.SKELETON_SKULL));
            if (type.equals("miner")) {
                as.getEquipment().setItemInMainHand(minerTool(1));
            }
            as.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            as.getEquipment().setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            as.getEquipment().setBoots(new ItemStack(Material.LEATHER_BOOTS));
            
            hand.setAmount(hand.getAmount() - 1);
            countByOwner.merge(island.owner(), 1, Integer::sum);
            if (plugin.getSessionListener() != null) {
                plugin.getSessionListener().updateScoreboard(player);
            }
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
                        ItemStack workerItem = createItem(type, as);
                        as.remove();
                        countByOwner.merge(UUID.fromString(ownerStr), -1, Integer::sum);
                        if (plugin.getSessionListener() != null) {
                            plugin.getSessionListener().updateScoreboard(player);
                        }
                        player.getInventory().addItem(workerItem);
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
                
                if (!type.equals("collector")) continue;
                int range = 1 + ((workerLevel((ArmorStand) e) - 1) / 5);
                
                if (Math.abs(e.getLocation().getBlockX() - genLoc.getBlockX()) <= range && 
                    Math.abs(e.getLocation().getBlockZ() - genLoc.getBlockZ()) <= range) {
                    if (!addToInventory((ArmorStand) e, (int) Math.min(Integer.MAX_VALUE, moneyValue))) continue;

                    milestones.addWorkerCollection(owner, 1);
                    var workerPdc = ((ArmorStand) e).getPersistentDataContainer();
                    workerPdc.set(generatedKey, PersistentDataType.LONG,
                        workerPdc.getOrDefault(generatedKey, PersistentDataType.LONG, 0L) + 1L);
                    addWorkerProgress((ArmorStand) e, 1);
                    
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

    private int workerLevel(ArmorStand worker) {
        return Math.min(50, worker.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 1));
    }

    private int workerProgress(ArmorStand worker) {
        return worker.getPersistentDataContainer().getOrDefault(progressKey, PersistentDataType.INTEGER, 0);
    }

    private int nextLevelRequirement(String type, int level) {
        return type.equals("collector") ? 10 + (level * 10) : 5 + (level * 5);
    }

    private String selectedOreName(ArmorStand miner) {
        return selectedOre(miner).name().replace('_', ' ');
    }

    private List<Material> unlockedOres(int level) {
        List<Material> ores = new java.util.ArrayList<>();
        ores.add(Material.COAL_ORE);
        if (level >= 5) ores.add(Material.IRON_ORE);
        if (level >= 10) ores.add(Material.COPPER_ORE);
        if (level >= 15) ores.add(Material.GOLD_ORE);
        if (level >= 20) ores.add(Material.REDSTONE_ORE);
        if (level >= 25) ores.add(Material.DIAMOND_ORE);
        if (level >= 30) ores.add(Material.EMERALD_ORE);
        return ores;
    }

    private Material selectedOre(ArmorStand miner) {
        List<Material> unlocked = unlockedOres(workerLevel(miner));
        String selected = miner.getPersistentDataContainer().get(selectedOreKey, PersistentDataType.STRING);
        if (selected != null) {
            try {
                Material material = Material.valueOf(selected);
                if (unlocked.contains(material)) return material;
            } catch (IllegalArgumentException ignored) { }
        }
        Material material = unlocked.get(unlocked.size() - 1);
        miner.getPersistentDataContainer().set(selectedOreKey, PersistentDataType.STRING, material.name());
        return material;
    }

    private ItemStack createOreSelector(ArmorStand miner) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        Material selected = selectedOre(miner);
        meta.setDisplayName("§bMineral selection");
        meta.setLore(List.of("§7Selected: §f" + selected.name().replace("_ORE", ""),
            "§7Click to cycle unlocked ores.", "§7Unlocked: §f" + unlockedOres(workerLevel(miner)).stream()
                .map(material -> material.name().replace("_ORE", "")).toList()));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack minerTool(int level) {
        Material material = level >= 21 ? Material.NETHERITE_PICKAXE
            : level >= 16 ? Material.DIAMOND_PICKAXE
            : level >= 11 ? Material.GOLDEN_PICKAXE
            : level >= 6 ? Material.IRON_PICKAXE
            : level >= 3 ? Material.STONE_PICKAXE
            : Material.WOODEN_PICKAXE;
        ItemStack tool = new ItemStack(material);
        ItemMeta meta = tool.getItemMeta();
        meta.setUnbreakable(true);
        tool.setItemMeta(meta);
        return tool;
    }

    private void addWorkerProgress(ArmorStand worker, int amount) {
        var pdc = worker.getPersistentDataContainer();
        int progress = pdc.getOrDefault(progressKey, PersistentDataType.INTEGER, 0) + amount;
        int level = workerLevel(worker);
        String type = pdc.get(minionKey, PersistentDataType.STRING);
        while (level < 50 && progress >= nextLevelRequirement(type, level)) {
            progress -= nextLevelRequirement(type, level);
            level++;
        }
        if (level >= 50) progress = 0;
        pdc.set(progressKey, PersistentDataType.INTEGER, progress);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        worker.setCustomName(type.equals("miner") ? "§bMiner" : "§eCollector");
        worker.setCustomNameVisible(false);
        if (type.equals("miner")) worker.getEquipment().setItemInMainHand(minerTool(level));
    }

    private void runMiners() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand miner : world.getEntitiesByClass(ArmorStand.class)) {
                if (!"miner".equals(miner.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING))) continue;
                int level = workerLevel(miner);
                miner.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
                miner.getEquipment().setItemInMainHand(minerTool(level));
                Material ore = selectedOre(miner);
                Location base = miner.getLocation().getBlock().getLocation();
                Location target = null;
                for (int x = -1; x <= 1 && target == null; x++) for (int z = -1; z <= 1; z++) {
                    if (x == 0 && z == 0) continue;
                    Location candidate = base.clone().add(x, 0, z);
                    if (candidate.getBlock().getType().isAir()) { target = candidate; break; }
                }
                if (target == null) continue;
                target.getBlock().setType(ore, false);
                miner.getPersistentDataContainer().set(generatedKey, PersistentDataType.LONG,
                    miner.getPersistentDataContainer().getOrDefault(generatedKey, PersistentDataType.LONG, 0L) + 1L);
                milestones.addWorkerCollection(
                    UUID.fromString(miner.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING)), 1);
                addWorkerProgress(miner, 1);
            }
        }
    }

    private ItemStack createStatusItem(ArmorStand worker) {
        String type = worker.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
        int level = workerLevel(worker);
        long amount = worker.getPersistentDataContainer().getOrDefault(generatedKey, PersistentDataType.LONG, 0L);
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta meta = clock.getItemMeta();
        meta.setDisplayName("§eWorker Statistics");
        meta.setLore(List.of("§7Level: §f" + level,
            "§7" + (type.equals("miner") ? "Ores generated" : "Items collected") + ": §f" + amount,
            "§7Progress: §f" + workerProgress(worker) + "/" + nextLevelRequirement(type, level),
            "§7Upgrades: §f" + worker.getPersistentDataContainer().getOrDefault(upgradeKey, PersistentDataType.STRING, "None")));
        clock.setItemMeta(meta);
        return clock;
    }

    private boolean addToInventory(ArmorStand collector, int value) {
        Inventory inventory = Bukkit.createInventory(null, 27);
        loadInventory(collector, inventory);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || !existing.hasItemMeta()) continue;
            Integer existingValue = existing.getItemMeta().getPersistentDataContainer()
                .get(moneyKey, PersistentDataType.INTEGER);
            if (existingValue == null || existingValue != value) continue;
            if (existing.getAmount() >= existing.getMaxStackSize()) continue;

            ItemMeta meta = existing.getItemMeta();
            // The physical stack is authoritative: old collectors could store
            // the whole logical amount in the metadata of their first stack.
            int newAmount = existing.getAmount() + 1;
            meta.getPersistentDataContainer().set(amountKey, PersistentDataType.LONG, (long) newAmount);
            existing.setAmount(newAmount);
            existing.setItemMeta(meta);
            inventory.setItem(slot, existing);
            saveInventory(collector, inventory);
            refreshOpenInventory(collector, inventory);
            return true;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {
                inventory.setItem(slot, createMoney(value, 1));
                saveInventory(collector, inventory);
                refreshOpenInventory(collector, inventory);
                return true;
            }
        }
        return false;
    }

    private void refreshOpenInventory(ArmorStand collector, Inventory inventory) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory open = player.getOpenInventory().getTopInventory();
            if (open.getHolder() instanceof CollectorHolder holder
                && holder.id().equals(collector.getUniqueId()) && !holder.upgrades()) {
                open.setContents(inventory.getContents());
                player.updateInventory();
            }
        }
    }

    private ItemStack createMoney(int value, long amount) {
        Material material = value >= 10000 ? Material.IRON_BLOCK
            : value >= 1000 ? Material.NAME_TAG
            : value > 100 ? Material.GOLD_INGOT : Material.PAPER;
        String name = value >= 10000 ? "Briefcase"
            : value >= 1000 ? "Credit Card"
            : value > 100 ? "Gold Bar" : "Bill";
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§2$§a" + PlayerSessionListener.formatValue(value) + " §a" + name);
        meta.setLore(List.of("§7Right-click to deposit!"));
        meta.getPersistentDataContainer().set(moneyKey, PersistentDataType.INTEGER, value);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.LONG, Math.max(1L, amount));
        item.setItemMeta(meta);
        return item;
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

    private void loadInventory(ArmorStand stand, Inventory inventory) {
                String encoded = stand.getPersistentDataContainer().get(inventoryKey, PersistentDataType.STRING);
                if (encoded == null) return;
                try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
                    Object value = input.readObject();
                    if (value instanceof ItemStack[] contents) inventory.setContents(contents);
                } catch (IOException | ClassNotFoundException | IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Inventário de coletor inválido: " + stand.getUniqueId());
                }
    }

    private void saveInventory(ArmorStand stand, Inventory inventory) {
                try {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
                        output.writeObject(inventory.getContents());
                    }
                    stand.getPersistentDataContainer().set(inventoryKey, PersistentDataType.STRING,
                        Base64.getEncoder().encodeToString(bytes.toByteArray()));
                } catch (IOException e) {
                    plugin.getLogger().warning("Falha ao salvar inventário do coletor: " + e.getMessage());
                }
    }

    private record CollectorHolder(UUID id, boolean upgrades) implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
