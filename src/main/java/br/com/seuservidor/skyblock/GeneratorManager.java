package br.com.seuservidor.skyblock;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import net.kyori.adventure.text.Component;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class GeneratorManager implements Listener {
    private final SkyblockPlugin plugin;
    private final IslandManager islands;
    public final NamespacedKey generatorKey;
    public final NamespacedKey moneyKey;
    public final NamespacedKey amountKey;
    private final Map<String, GeneratorType> types = new LinkedHashMap<>();
    private final Map<Location, PlacedGenerator> placed = new HashMap<>();
    private final Map<UUID, Integer> countsByOwner = new HashMap<>();
    private final File dataFile;
    private final MinionManager minions;
    private final int outputSearchLimit;
    private final int maxItemsPerIsland;
    private final int maxEntitiesPerIsland;

    public GeneratorManager(SkyblockPlugin plugin, IslandManager islands, MinionManager minions) {
        this.plugin = plugin; this.islands = islands; this.minions = minions;
        this.generatorKey = new NamespacedKey(plugin, "generator_type");
        this.moneyKey = new NamespacedKey(plugin, "money_value");
        this.amountKey = new NamespacedKey(plugin, "money_amount");
        this.dataFile = new File(plugin.getDataFolder(), "generators.yml");
        this.outputSearchLimit = Math.max(1, plugin.getConfig().getInt("generator-output-search-limit", 16));
        this.maxItemsPerIsland = Math.max(1, plugin.getConfig().getInt("limits.max-item-entities-per-island", 100));
        this.maxEntitiesPerIsland = Math.max(1, plugin.getConfig().getInt("limits.max-entities-per-island", 200));
        plugin.saveDefaultConfig();
        loadTypes(); load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    public int getCount(UUID owner) {
        return countsByOwner.getOrDefault(owner, 0);
    }

    public long getMoneyPerHour(UUID owner) {
        long total = 0;
        for (PlacedGenerator generator : placed.values()) {
            if (!generator.owner().equals(owner)) continue;
            GeneratorType type = types.get(generator.type());
            if (type == null || type.intervalSeconds() <= 0) continue;
            total += (long) ((3600.0 / type.intervalSeconds())
                * type.noteAmount() * type.noteValue());
        }
        return total;
    }
    
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        
        PlacedGenerator generator = placed.get(block.getLocation());
        if (generator != null) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                if (!islands.isMember(player.getUniqueId(), generator.owner())) {
                    event.setCancelled(true);
                    player.sendMessage("§cOnly coop members can remove this generator.");
                    return;
                }
                event.setCancelled(true);
                ItemStack genItem = removeGenerator(block);
                if (genItem != null) {
                    block.setType(Material.AIR);
                    player.getInventory().addItem(genItem);
                    player.playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                    
                    if (plugin.getSessionListener() != null) plugin.getSessionListener().updateScoreboard(player);
                }
            }
        }
    }
    
    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(moneyKey, PersistentDataType.INTEGER)) {
            Integer amt = item.getItemMeta().getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
            if (amt != null && amt > 1) { 
                event.setCancelled(true);
                event.getItem().remove();
                
                int val = item.getItemMeta().getPersistentDataContainer().get(moneyKey, PersistentDataType.INTEGER);
                int remaining = amt;
                while (remaining > 0) {
                    int chunk = Math.min(64, remaining);
                    ItemStack stack = createMoney(val, 1);
                    stack.setAmount(chunk);
                    player.getInventory().addItem(stack);
                    remaining -= chunk;
                }
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.2f, 2.0f);
            }
        }
    }

    public void resetPlayer(UUID owner) {
        placed.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        countsByOwner.remove(owner);
        save();
    }
    
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (placed.containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }
    
    private void loadTypes() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("generators"); if (section == null) return;
        for (String id : section.getKeys(false)) {
            String base = "generators." + id + ".";
            try { 
                types.put(id, new GeneratorType(id, 
                    plugin.getConfig().getString(base + "display-name", id), 
                    plugin.getConfig().getLong(base + "interval-seconds", 10), 
                    plugin.getConfig().getInt(base + "note-value", 10),
                    plugin.getConfig().getInt(base + "note-amount", 1),
                    Material.valueOf(plugin.getConfig().getString(base + "block-type", "STONE"))
                )); 
            }
            catch (Exception e) { plugin.getLogger().warning("Invalid generator in config: " + id); }
        }
    }
    
    public Set<String> typeIds() { return Collections.unmodifiableSet(types.keySet()); }
    
    public ItemStack createItem(String id) {
        GeneratorType type = types.get(id); if (type == null) return null;
        ItemStack item = new ItemStack(type.blockType()); ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', type.name()));
        
        int tier = new java.util.ArrayList<>(types.keySet()).indexOf(id) + 1;
        
        meta.setLore(List.of(
            "§8[Tier " + tier + "]",
            "§7Place on your island",
            "§7Produces: §a" + type.noteAmount() + "x §2$§a" + type.noteValue() + " §7every §f" + type.intervalSeconds() + "s"
        ));
        
        meta.getPersistentDataContainer().set(generatorKey, PersistentDataType.STRING, id); item.setItemMeta(meta); return item;
    }
    
    public ItemStack createMoney(int value, int amount) {
        Material mat = Material.PAPER;
        String name = "Bill";
        if (value >= 10000) { mat = Material.CHEST; name = "Briefcase"; }
        else if (value >= 1000) { mat = Material.NAME_TAG; name = "Credit Card"; }
        else if (value > 100) { mat = Material.GOLD_INGOT; name = "Gold Bar"; }
        
        ItemStack money = new ItemStack(mat);
        ItemMeta meta = money.getItemMeta();
        meta.setDisplayName("§2$§a" + value + " §a" + name);
        meta.setLore(List.of("§7Right-click to deposit!"));
        meta.getPersistentDataContainer().set(moneyKey, PersistentDataType.INTEGER, value);
        meta.getPersistentDataContainer().set(amountKey, PersistentDataType.INTEGER, amount);
        money.setItemMeta(meta);
        return money;
    }

    public String itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(generatorKey, PersistentDataType.STRING);
    }
    
    public boolean place(Player player, Block block, ItemStack item) {
        String type = itemType(item); if (type == null) return false;
        Island island = islands.getForPlayer(player.getUniqueId()).orElse(null);
        if (island == null || !island.contains(block.getLocation(), islands.radius())) {
            player.sendActionBar(Component.text("§cOnly place on your island!")); return true;
        }
        
        int maxGens = plugin.getMilestones().getGeneratorLimit(island.owner());
        if (getCount(island.owner()) >= maxGens) {
            player.sendMessage("§cYou reached your generator limit (" + maxGens + ")! Check /milestones to upgrade.");
            return true;
        }
        
        placed.put(block.getLocation(), new PlacedGenerator(type, island.owner(), System.currentTimeMillis()));
        countsByOwner.merge(island.owner(), 1, Integer::sum);
        save();
        if (plugin.getSessionListener() != null) plugin.getSessionListener().updateScoreboard(player);
        return false;
    }
    
    public ItemStack removeGenerator(Block block) {
        PlacedGenerator generator = placed.remove(block.getLocation());
        if (generator == null) return null;
        countsByOwner.merge(generator.owner(), -1, Integer::sum);
        save();
        return createItem(generator.type());
    }
    
    public void start() { 
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L); 
    }
    
    private void tick() {
        long now = System.currentTimeMillis();
        boolean needsSave = false;
        
        Set<UUID> activeIslands = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!islands.isSkyblockWorld(p.getLocation())) continue;
            islands.getForPlayer(p.getUniqueId())
                .filter(island -> island.contains(p.getLocation(), islands.radius()))
                .ifPresent(island -> activeIslands.add(island.owner()));
        }

        for (Iterator<Map.Entry<Location, PlacedGenerator>> it = placed.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Location, PlacedGenerator> entry = it.next(); 
            Block block = entry.getKey().getBlock(); 
            PlacedGenerator generator = entry.getValue(); 
            GeneratorType type = types.get(generator.type());
            
            if (type == null || block.getType() != type.blockType()) {
                it.remove();
                countsByOwner.merge(generator.owner(), -1, Integer::sum);
                needsSave = true;
                continue;
            }
            if (!activeIslands.contains(generator.owner())) continue;
            
            if (now - generator.lastProduced() < type.intervalSeconds() * 1000) continue;
            
            long totalValue = (long) type.noteAmount() * type.noteValue();
            if (minions.autoSell(block.getLocation(), totalValue, generator.owner())) {
                entry.setValue(generator.withLastProduced(now));
                continue;
            }
            
            Block output = block.getRelative(0, 1, 0);
            int searched = 0;
            while (output.getType().isSolid() && searched < outputSearchLimit && output.getY() < output.getWorld().getMaxHeight() - 1) {
                output = output.getRelative(0, 1, 0);
                searched++;
            }
            if (output.getType().isSolid()) {
                entry.setValue(generator.withLastProduced(now));
                continue;
            }
            
            Island island = islands.get(generator.owner()).orElse(null);
            if (island == null) continue;
            long islandItems = output.getWorld().getEntitiesByClass(Item.class).stream()
                .filter(item -> island.contains(item.getLocation(), islands.radius())).count();
            long islandEntities = output.getWorld().getEntities().stream()
                .filter(entity -> island.contains(entity.getLocation(), islands.radius())).count();
            if (islandItems >= maxItemsPerIsland || islandEntities >= maxEntitiesPerIsland) {
                continue;
            }
            Collection<org.bukkit.entity.Entity> nearby = output.getWorld().getNearbyEntities(output.getLocation().add(0.5, 0.2, 0.5), 1.5, 1.5, 1.5, e -> e instanceof Item);
            boolean merged = false;
            for (org.bukkit.entity.Entity e : nearby) {
                Item itemEntity = (Item) e;
                ItemStack stack = itemEntity.getItemStack();
                if (stack.hasItemMeta()) {
                    Integer val = stack.getItemMeta().getPersistentDataContainer().get(moneyKey, PersistentDataType.INTEGER);
                    if (val != null && val == type.noteValue()) {
                        Integer amt = stack.getItemMeta().getPersistentDataContainer().get(amountKey, PersistentDataType.INTEGER);
                        if (amt == null) amt = stack.getAmount();
                        
                        int total = amt + type.noteAmount(); 
                        ItemStack newStack = createMoney(val, total);
                        itemEntity.setItemStack(newStack);
                        itemEntity.setCustomName("§2$§a" + val + " §8(x" + total + ")");
                        merged = true;
                        break;
                    }
                }
            }
            
            if (!merged) {
                ItemStack money = createMoney(type.noteValue(), type.noteAmount());
                Item entity = output.getWorld().dropItem(output.getLocation().add(0.5, 0.2, 0.5), money);
                entity.setCustomName("§2$§a" + type.noteValue() + " §8(x" + type.noteAmount() + ")");
                entity.setCustomNameVisible(true);
                entity.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
            }
            
            entry.setValue(generator.withLastProduced(now));
        }
        if (needsSave) save();
    }
    
    private void load() {
        if (!dataFile.exists()) return; 
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile); 
        ConfigurationSection section = data.getConfigurationSection("placed"); if (section == null) return;
        for (String key : section.getKeys(false)) { 
            String path = "placed." + key + "."; 
            World world = Bukkit.getWorld(data.getString(path + "world", "skyblock")); if (world == null) continue;
            try { 
                placed.put(new Location(world, data.getInt(path + "x"), data.getInt(path + "y"), data.getInt(path + "z")), 
                    new PlacedGenerator(data.getString(path + "type"), UUID.fromString(data.getString(path + "owner")), data.getLong(path + "last")));
                countsByOwner.merge(UUID.fromString(data.getString(path + "owner")), 1, Integer::sum);
            } catch (IllegalArgumentException ignored) { }
        }
    }
    
    public void save() { 
        YamlConfiguration data = new YamlConfiguration(); int i = 0; 
        for (Map.Entry<Location, PlacedGenerator> entry : placed.entrySet()) { 
            String p = "placed." + i++ + "."; Location l = entry.getKey(); PlacedGenerator g = entry.getValue(); 
            data.set(p + "world", l.getWorld().getName()); data.set(p + "x", l.getBlockX()); data.set(p + "y", l.getBlockY()); data.set(p + "z", l.getBlockZ()); 
            data.set(p + "type", g.type()); data.set(p + "owner", g.owner().toString()); data.set(p + "last", g.lastProduced());
        } 
        try { data.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("Falha ao salvar geradores: " + e.getMessage()); } 
    }
    
    public GeneratorType getType(String id) { return types.get(id); }
    public record GeneratorType(String id, String name, long intervalSeconds, int noteValue, int noteAmount, Material blockType) { }
    private record PlacedGenerator(String type, UUID owner, long lastProduced) { 
        PlacedGenerator withLastProduced(long value) { return new PlacedGenerator(type, owner, value); } 
    }
}
