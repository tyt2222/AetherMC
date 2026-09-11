package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AuctionManager implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = "§8Player Market";
    private static final int PREV_SLOT = 45;
    private static final int ALL_SLOT = 47;
    private static final int SELL_SLOT = 49;
    private static final int MY_SLOT = 51;
    private static final int NEXT_SLOT = 53;
    private static final int[] LISTING_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    };

    private final SkyblockPlugin plugin;
    private final EconomyManager economy;
    private final PlayerSessionListener sessionListener;
    private final File dataFile;
    private final List<Listing> listings = new ArrayList<>();
    private final Map<UUID, MarketView> views = new HashMap<>();
    private int nextId = 1;

    public AuctionManager(SkyblockPlugin plugin, EconomyManager economy, PlayerSessionListener sessionListener) {
        this.plugin = plugin;
        this.economy = economy;
        this.sessionListener = sessionListener;
        this.dataFile = new File(plugin.getDataFolder(), "auctions.yml");
        load();
        plugin.getCommand("market").setExecutor(this);
        plugin.getCommand("market").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return "sell".startsWith(args[0].toLowerCase()) ? List.of("sell") : List.of();
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command is for players only.");
            return true;
        }

        if (args.length == 0) {
            openMarket(player, MarketTab.ALL, 0);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            sellHandItem(player, args[1]);
            return true;
        }

        player.sendMessage("§eUse: /market or /market sell <price>");
        return true;
    }

    private void sellHandItem(Player player, String priceText) {
        long price;
        try {
            price = Long.parseLong(priceText);
        } catch (NumberFormatException ignored) {
            player.sendMessage("§cInvalid price.");
            return;
        }

        if (price <= 0) {
            player.sendMessage("§cPrice must be greater than zero.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.getAmount() <= 0) {
            player.sendMessage("§cHold the item stack you want to sell.");
            return;
        }

        ItemStack listed = item.clone();
        listings.add(new Listing(nextId++, player.getUniqueId(), player.getName(), price, listed));
        player.getInventory().setItemInMainHand(null);
        save();
        player.sendMessage("§aListed " + listed.getAmount() + "x " + listed.getType().name() + " for $" + PlayerSessionListener.formatValue(price) + ".");
    }

    private void openMarket(Player player, MarketTab tab, int page) {
        List<Listing> visible = visibleListings(player, tab);
        int maxPage = Math.max(0, (visible.size() - 1) / LISTING_SLOTS.length);
        int safePage = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54, TITLE);
        fillBorders(inv);

        MarketView view = new MarketView(tab, safePage, new HashMap<>());
        int start = safePage * LISTING_SLOTS.length;
        int end = Math.min(visible.size(), start + LISTING_SLOTS.length);
        for (int i = start; i < end; i++) {
            Listing listing = visible.get(i);
            int slot = LISTING_SLOTS[i - start];
            inv.setItem(slot, displayItem(player, listing));
            view.slotListings().put(slot, listing.id());
        }

        inv.setItem(PREV_SLOT, button(Material.ARROW, "§ePrevious Page", "§7Page " + (safePage + 1) + "/" + (maxPage + 1)));
        inv.setItem(ALL_SLOT, button(Material.CHEST, tab == MarketTab.ALL ? "§a§lAll Listings" : "§aAll Listings", "§7Browse every public listing."));
        inv.setItem(SELL_SLOT, button(Material.EMERALD, "§a§lSell Items", "§7Hold an item stack and use:", "§f/market sell <price>"));
        inv.setItem(MY_SLOT, button(Material.ENDER_CHEST, tab == MarketTab.MY ? "§b§lMy Listings" : "§bMy Listings", "§7Click your own listings to cancel."));
        inv.setItem(NEXT_SLOT, button(Material.ARROW, "§eNext Page", "§7Page " + (safePage + 1) + "/" + (maxPage + 1)));

        views.put(player.getUniqueId(), view);
        player.openInventory(inv);
    }

    private List<Listing> visibleListings(Player player, MarketTab tab) {
        return listings.stream()
            .filter(listing -> tab == MarketTab.ALL || listing.seller().equals(player.getUniqueId()))
            .sorted(Comparator.comparingInt(Listing::id))
            .toList();
    }

    private ItemStack displayItem(Player viewer, Listing listing) {
        ItemStack display = listing.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add("§7Seller: §f" + listing.sellerName());
        lore.add("§7Price: §a$" + PlayerSessionListener.formatValue(listing.price()));
        lore.add(listing.seller().equals(viewer.getUniqueId()) ? "§cClick to cancel listing." : "§eClick to buy!");
        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        MarketView view = views.getOrDefault(player.getUniqueId(), new MarketView(MarketTab.ALL, 0, new HashMap<>()));
        int slot = event.getRawSlot();

        if (slot == ALL_SLOT) {
            openMarket(player, MarketTab.ALL, 0);
            return;
        }
        if (slot == MY_SLOT) {
            openMarket(player, MarketTab.MY, 0);
            return;
        }
        if (slot == PREV_SLOT) {
            openMarket(player, view.tab(), view.page() - 1);
            return;
        }
        if (slot == NEXT_SLOT) {
            openMarket(player, view.tab(), view.page() + 1);
            return;
        }

        Integer listingId = view.slotListings().get(slot);
        if (listingId == null) return;
        Listing listing = findListing(listingId);
        if (listing == null) {
            player.sendMessage("§cThis listing is no longer available.");
            openMarket(player, view.tab(), view.page());
            return;
        }

        if (listing.seller().equals(player.getUniqueId())) {
            cancelListing(player, listing, view);
            return;
        }

        buyListing(player, listing, view);
    }

    private void cancelListing(Player player, Listing listing, MarketView view) {
        listings.removeIf(current -> current.id() == listing.id());
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(listing.item().clone());
        for (ItemStack item : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        save();
        player.sendMessage("§aListing cancelled.");
        openMarket(player, view.tab(), view.page());
    }

    private void buyListing(Player buyer, Listing listing, MarketView view) {
        if (!listings.removeIf(current -> current.id() == listing.id())) {
            buyer.sendMessage("§cThis listing is no longer available.");
            openMarket(buyer, view.tab(), view.page());
            return;
        }

        if (!economy.removeBalance(buyer.getUniqueId(), listing.price())) {
            listings.add(listing);
            buyer.sendMessage("§cYou don't have enough money.");
            return;
        }

        economy.addBalance(listing.seller(), listing.price());
        Map<Integer, ItemStack> overflow = buyer.getInventory().addItem(listing.item().clone());
        for (ItemStack item : overflow.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), item);
        }

        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            seller.sendMessage("§aYour market listing sold for $" + PlayerSessionListener.formatValue(listing.price()) + ".");
            sessionListener.updateScoreboard(seller);
        }

        buyer.sendMessage("§aPurchase complete.");
        sessionListener.updateScoreboard(buyer);
        save();
        openMarket(buyer, view.tab(), view.page());
    }

    private Listing findListing(int id) {
        for (Listing listing : listings) {
            if (listing.id() == id) return listing;
        }
        return null;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack bg = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, bg);
        for (int i = 45; i < 54; i++) inv.setItem(i, bg);
        for (int i = 9; i <= 36; i += 9) inv.setItem(i, bg);
        for (int i = 17; i <= 44; i += 9) inv.setItem(i, bg);
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = DataFileUtil.load(dataFile);
        nextId = data.getInt("next-id", 1);
        ConfigurationSection section = data.getConfigurationSection("listings");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "listings." + key + ".";
            try {
                ItemStack item = data.getItemStack(path + "item");
                if (item == null || item.getType() == Material.AIR) continue;
                listings.add(new Listing(
                    data.getInt(path + "id"),
                    UUID.fromString(data.getString(path + "seller")),
                    data.getString(path + "seller-name", "Unknown"),
                    data.getLong(path + "price"),
                    item
                ));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("next-id", nextId);
        int index = 0;
        for (Listing listing : listings) {
            String path = "listings." + index++ + ".";
            data.set(path + "id", listing.id());
            data.set(path + "seller", listing.seller().toString());
            data.set(path + "seller-name", listing.sellerName());
            data.set(path + "price", listing.price());
            data.set(path + "item", listing.item());
        }

        try {
            DataFileUtil.save(data, dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save auctions: " + e.getMessage());
        }
    }

    public void removeListings(UUID seller) {
        if (listings.removeIf(listing -> listing.seller().equals(seller))) save();
    }

    private enum MarketTab { ALL, MY }
    private record Listing(int id, UUID seller, String sellerName, long price, ItemStack item) { }
    private record MarketView(MarketTab tab, int page, Map<Integer, Integer> slotListings) { }
}
