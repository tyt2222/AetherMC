package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class ShopCommand implements CommandExecutor, Listener {
    private final SkyblockPlugin plugin;
    private final GeneratorManager generators;
    private final MinionManager minions;
    private final EconomyManager economy;
    private final PlayerSessionListener sessionListener;
    
    private final Map<String, Long> generatorPrices = new LinkedHashMap<>();
    private final Map<String, Long> blockPrices = new LinkedHashMap<>();
    private final Map<String, Long> minionPrices = new LinkedHashMap<>();

    public ShopCommand(SkyblockPlugin plugin, GeneratorManager generators, MinionManager minions, EconomyManager economy, PlayerSessionListener sessionListener) {
        this.plugin = plugin;
        this.generators = generators;
        this.minions = minions;
        this.economy = economy;
        this.sessionListener = sessionListener;
        
        plugin.getCommand("shop").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadPrices();
    }

    private void loadPrices() {
        loadPriceSection("shop.generators", generatorPrices);
        loadPriceSection("shop.blocks", blockPrices);
        loadPriceSection("shop.minions", minionPrices);
        loadDefaultPricesIfMissing();
    }

    private void loadPriceSection(String path, Map<String, Long> prices) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            prices.put(key, section.getLong(key));
        }
    }

    private void loadDefaultPricesIfMissing() {
        if (generatorPrices.isEmpty()) {
            generatorPrices.put("coal", 100L);
            generatorPrices.put("iron", 500L);
            generatorPrices.put("gold", 1000L);
            generatorPrices.put("diamond", 5000L);
            generatorPrices.put("emerald", 10000L);
            generatorPrices.put("lapis", 30000L);
            generatorPrices.put("redstone", 50000L);
            generatorPrices.put("quartz", 100000L);
            generatorPrices.put("netherite", 500000L);
            generatorPrices.put("aether", 1000000L);
        }

        if (blockPrices.isEmpty()) {
            blockPrices.put("STONE", 10L);
            blockPrices.put("OAK_PLANKS", 10L);
            blockPrices.put("DIRT", 5L);
            blockPrices.put("COBBLESTONE", 5L);
            blockPrices.put("GLASS", 15L);
            blockPrices.put("BRICKS", 20L);
            blockPrices.put("QUARTZ_BLOCK", 50L);
            blockPrices.put("SMOOTH_STONE", 15L);
            blockPrices.put("SPRUCE_PLANKS", 10L);
            blockPrices.put("OAK_LOG", 20L);
        }

        if (minionPrices.isEmpty()) {
            minionPrices.put("collector_3x3", 500000L);
            minionPrices.put("collector_5x5", 5000000L);
        }
    }

    private void fillBorders(Inventory inv) {
        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.setDisplayName(" ");
        bg.setItemMeta(meta);
        for (int i = 0; i < 9; i++) inv.setItem(i, bg);
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);
        for (int i = 9; i <= 36; i+=9) inv.setItem(i, bg);
        for (int i = 17; i <= 44; i+=9) inv.setItem(i, bg);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            openCategories(player);
        }
        return true;
    }

    private void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Shop Categories");
        fillBorders(inv);
        
        ItemStack gens = new ItemStack(Material.DIAMOND_ORE);
        ItemMeta gensMeta = gens.getItemMeta();
        gensMeta.setDisplayName("§a§lGenerators");
        gensMeta.setLore(List.of("§7Click to view generators"));
        gens.setItemMeta(gensMeta);
        
        ItemStack blocks = new ItemStack(Material.BRICKS);
        ItemMeta blocksMeta = blocks.getItemMeta();
        blocksMeta.setDisplayName("§b§lBuilding Blocks");
        blocksMeta.setLore(List.of("§7Click to view blocks"));
        blocks.setItemMeta(blocksMeta);

        ItemStack mins = new ItemStack(Material.ARMOR_STAND);
        ItemMeta minsMeta = mins.getItemMeta();
        minsMeta.setDisplayName("§e§lWorkers / Minions");
        minsMeta.setLore(List.of("§7Click to view minions"));
        mins.setItemMeta(minsMeta);
        
        inv.setItem(20, gens);
        inv.setItem(22, blocks);
        inv.setItem(24, mins);
        
        player.openInventory(inv);
    }

    private void openGenerators(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Generator Shop");
        fillBorders(inv);
        
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        int i = 0;
        
        for (Map.Entry<String, Long> entry : generatorPrices.entrySet()) {
            if (i >= slots.length) break;
            
            String id = entry.getKey();
            long price = entry.getValue();
            GeneratorManager.GeneratorType type = generators.getType(id);
            if (type == null) continue;
            
            ItemStack item = new ItemStack(type.blockType());
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', type.name()));
            int tier = new java.util.ArrayList<>(generatorPrices.keySet()).indexOf(id) + 1;
            
            meta.setLore(List.of(
                "§8[Tier " + tier + "]",
                "§7Produces: §a" + type.noteAmount() + "x §2$§a" + type.noteValue() + " §7every §f" + type.intervalSeconds() + "s",
                "",
                "§fPrice: §a$" + PlayerSessionListener.formatValue(price),
                "§eClick to buy!"
            ));
            item.setItemMeta(meta);
            
            inv.setItem(slots[i++], item);
        }
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack to Categories");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        
        player.openInventory(inv);
    }

    private void openBlocks(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Blocks Shop");
        fillBorders(inv);
        
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int i = 0;
        
        for (Map.Entry<String, Long> entry : blockPrices.entrySet()) {
            if (i >= slots.length) break;
            Material mat = Material.valueOf(entry.getKey());
            long price = entry.getValue();
            
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of("", "§fPrice: §a$" + PlayerSessionListener.formatValue(price), "§eClick to buy 64x!"));
            item.setItemMeta(meta);
            
            inv.setItem(slots[i++], item);
        }
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack to Categories");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        
        player.openInventory(inv);
    }

    private void openMinions(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, "§8Minion Shop");
        fillBorders(inv);
        
        ItemStack basic = minions.createItem("collector_3x3");
        ItemMeta basicMeta = basic.getItemMeta();
        List<String> basicLore = new java.util.ArrayList<>(basicMeta.getLore());
        basicLore.add("");
        basicLore.add("§fPrice: §a$" + PlayerSessionListener.formatValue(minionPrices.getOrDefault("collector_3x3", 500000L)));
        basicLore.add("§eClick to buy!");
        basicMeta.setLore(basicLore);
        basic.setItemMeta(basicMeta);
        
        ItemStack advanced = minions.createItem("collector_5x5");
        ItemMeta advMeta = advanced.getItemMeta();
        List<String> advLore = new java.util.ArrayList<>(advMeta.getLore());
        advLore.add("");
        advLore.add("§fPrice: §a$" + PlayerSessionListener.formatValue(minionPrices.getOrDefault("collector_5x5", 5000000L)));
        advLore.add("§eClick to buy!");
        advMeta.setLore(advLore);
        advanced.setItemMeta(advMeta);
        
        inv.setItem(21, basic);
        inv.setItem(23, advanced);
        
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName("§cBack to Categories");
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.equals("§8Shop Categories") || title.equals("§8Generator Shop") || title.equals("§8Minion Shop") || title.equals("§8Blocks Shop")) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR || event.getCurrentItem().getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            
            if (event.getCurrentItem().getType() == Material.ARROW && slot == 49) {
                openCategories(player);
                return;
            }
            
            if (title.equals("§8Shop Categories")) {
                if (slot == 20) openGenerators(player);
                else if (slot == 22) openBlocks(player);
                else if (slot == 24) openMinions(player);
            } 
            else if (title.equals("§8Blocks Shop")) {
                Material mat = event.getCurrentItem().getType();
                if (blockPrices.containsKey(mat.name())) {
                    long price = blockPrices.get(mat.name()) * 64;
                    if (economy.removeBalance(player.getUniqueId(), price)) {
                        player.getInventory().addItem(new ItemStack(mat, 64));
                        player.sendMessage("§aYou bought 64x " + mat.name() + " for $" + price + "!");
                        sessionListener.updateScoreboard(player);
                    } else {
                        player.sendMessage("§cYou don't have enough money! You need $" + price + ".");
                    }
                }
            }
            else if (title.equals("§8Generator Shop")) {
                String id = null;
                for (String key : generatorPrices.keySet()) {
                    if (generators.getType(key).blockType() == event.getCurrentItem().getType()) {
                        id = key;
                        break;
                    }
                }
                if (id != null) {
                    long price = generatorPrices.get(id);
                    if (economy.removeBalance(player.getUniqueId(), price)) {
                        player.getInventory().addItem(generators.createItem(id));
                        player.sendMessage("§aYou bought a " + id + " generator!");
                        sessionListener.updateScoreboard(player);
                    } else {
                        player.sendMessage("§cYou don't have enough money! You need $" + price + ".");
                    }
                }
            }
            else if (title.equals("§8Minion Shop")) {
                if (slot == 21) {
                    long price = minionPrices.getOrDefault("collector_3x3", 500000L);
                    if (economy.removeBalance(player.getUniqueId(), price)) {
                        player.getInventory().addItem(minions.createItem("collector_3x3"));
                        player.sendMessage("§aYou bought a Tier 1 Collector!");
                        sessionListener.updateScoreboard(player);
                    } else {
                        player.sendMessage("§cYou don't have enough money! You need $" + PlayerSessionListener.formatValue(price) + ".");
                    }
                } else if (slot == 23) {
                    long price = minionPrices.getOrDefault("collector_5x5", 5000000L);
                    if (economy.removeBalance(player.getUniqueId(), price)) {
                        player.getInventory().addItem(minions.createItem("collector_5x5"));
                        player.sendMessage("§aYou bought a Tier 2 Collector!");
                        sessionListener.updateScoreboard(player);
                    } else {
                        player.sendMessage("§cYou don't have enough money! You need $" + PlayerSessionListener.formatValue(price) + ".");
                    }
                }
            }
        }
    }
}
