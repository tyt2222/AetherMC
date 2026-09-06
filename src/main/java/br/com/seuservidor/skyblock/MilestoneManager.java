package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
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
import java.util.List;
import java.util.UUID;

public class MilestoneManager implements Listener {
    private final SkyblockPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;
    private boolean dirty;

    public MilestoneManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "milestones.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("milestones").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player player) {
                openMenu(player);
            }
            return true;
        });
    }

    private void load() {
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void save() {
        try { data.save(dataFile); dirty = false; } catch (IOException ignored) {}
    }

    public void addMoneyGenerated(UUID uuid, long amount) {
        long current = data.getLong(uuid.toString() + ".money", 0);
        data.set(uuid.toString() + ".money", current + amount);
        dirty = true;
    }

    public void addWorkerCollection(UUID uuid, long items) {
        long current = data.getLong(uuid.toString() + ".worker_items", 0);
        data.set(uuid.toString() + ".worker_items", current + items);
        dirty = true;
    }

    public void resetPlayer(UUID uuid) {
        data.set(uuid.toString(), null);
        save();
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public int getMoneyLevel(UUID uuid) {
        long money = data.getLong(uuid.toString() + ".money", 0);
        if (money >= 1_000_000_000L) return 5;
        if (money >= 100_000_000L) return 4;
        if (money >= 10_000_000L) return 3;
        if (money >= 1_000_000L) return 2;
        if (money >= 100_000L) return 1;
        return 0;
    }

    public int getWorkerLevel(UUID uuid) {
        long items = data.getLong(uuid.toString() + ".worker_items", 0);
        if (items >= 100_000L) return 5;
        if (items >= 50_000L) return 4;
        if (items >= 10_000L) return 3;
        if (items >= 1_000L) return 2;
        if (items >= 100L) return 1;
        return 0;
    }

    public int getPlaytimeLevel(Player player) {
        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long hours = ticks / (20 * 60 * 60);
        if (hours >= 48) return 5;
        if (hours >= 24) return 4;
        if (hours >= 12) return 3;
        if (hours >= 5) return 2;
        if (hours >= 1) return 1;
        return 0;
    }

    public int getOverallLevel(Player player) {
        UUID uuid = player.getUniqueId();
        return 1 + getMoneyLevel(uuid) + getWorkerLevel(uuid) + getPlaytimeLevel(player);
    }

    public int getGeneratorLimit(UUID uuid) {
        return 10 + (getMoneyLevel(uuid) * 2);
    }

    public int getWorkerLimit(UUID uuid) {
        int level = getWorkerLevel(uuid);
        if (level >= 5) return 4;
        if (level >= 3) return 3;
        if (level >= 1) return 2;
        return 1;
    }

    private String progressBar(int level) {
        StringBuilder bar = new StringBuilder("§8[");
        for (int i = 1; i <= 5; i++) {
            if (i <= level) bar.append("§a■");
            else bar.append("§7■");
        }
        bar.append("§8]");
        return bar.toString();
    }

    private long nextMoneyNeeded(UUID uuid) {
        long money = data.getLong(uuid.toString() + ".money", 0);
        long[] thresholds = {100_000L, 1_000_000L, 10_000_000L, 100_000_000L, 1_000_000_000L};
        for (long threshold : thresholds) {
            if (money < threshold) return threshold - money;
        }
        return 0;
    }

    private long nextWorkerNeeded(UUID uuid) {
        long items = data.getLong(uuid.toString() + ".worker_items", 0);
        long[] thresholds = {100L, 1_000L, 10_000L, 50_000L, 100_000L};
        for (long threshold : thresholds) {
            if (items < threshold) return threshold - items;
        }
        return 0;
    }

    private long nextPlaytimeHoursNeeded(Player player) {
        int ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long hours = ticks / (20 * 60 * 60);
        long[] thresholds = {1L, 5L, 12L, 24L, 48L};
        for (long threshold : thresholds) {
            if (hours < threshold) return threshold - hours;
        }
        return 0;
    }

    private String nextLine(String label, long amount, String suffix) {
        if (amount <= 0) return "§aMax level reached.";
        return "§7Next level: §e" + label + amount + suffix;
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§8Milestones");
        UUID uuid = player.getUniqueId();

        ItemStack bg = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        int playLvl = getPlaytimeLevel(player);
        ItemStack play = new ItemStack(Material.CLOCK);
        ItemMeta playMeta = play.getItemMeta();
        playMeta.setDisplayName("§ePlaytime Milestone");
        playMeta.setLore(List.of(
            progressBar(playLvl) + " §eLevel " + playLvl + "/5",
            "",
            "§7Track your dedication to the server.",
            "§7Level 1: 1h | Level 2: 5h | Level 3: 12h",
            "§7Level 4: 24h | Level 5: 48h",
            nextLine("", nextPlaytimeHoursNeeded(player), "h left"),
            "",
            "§8Visual milestone only."
        ));
        play.setItemMeta(playMeta);
        inv.setItem(11, play);

        int moneyLvl = getMoneyLevel(uuid);
        long moneyGen = data.getLong(uuid.toString() + ".money", 0);
        ItemStack money = new ItemStack(Material.GOLD_INGOT);
        ItemMeta moneyMeta = money.getItemMeta();
        moneyMeta.setDisplayName("§6Wealth Milestone");
        moneyMeta.setLore(List.of(
            progressBar(moneyLvl) + " §eLevel " + moneyLvl + "/5",
            "",
            "§7Track total money generated.",
            "§7Currently Generated: §e$" + PlayerSessionListener.formatValue(moneyGen),
            nextLine("$", nextMoneyNeeded(uuid), " left"),
            "",
            "§aReward:",
            "§7Increases Generator Limit (+2 per level)",
            "§7Current Limit: §e" + getGeneratorLimit(uuid)
        ));
        money.setItemMeta(moneyMeta);
        inv.setItem(13, money);

        int workLvl = getWorkerLevel(uuid);
        long workItems = data.getLong(uuid.toString() + ".worker_items", 0);
        ItemStack worker = new ItemStack(Material.ARMOR_STAND);
        ItemMeta workerMeta = worker.getItemMeta();
        workerMeta.setDisplayName("§cAutomation Milestone");
        workerMeta.setLore(List.of(
            progressBar(workLvl) + " §eLevel " + workLvl + "/5",
            "",
            "§7Track total items collected by Minions.",
            "§7Currently Collected: §e" + PlayerSessionListener.formatValue(workItems),
            nextLine("", nextWorkerNeeded(uuid), " collections left"),
            "",
            "§aReward:",
            "§7Increases Worker Limit (Up to 4)",
            "§7Current Limit: §e" + getWorkerLimit(uuid)
        ));
        worker.setItemMeta(workerMeta);
        inv.setItem(15, worker);

        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("§8Milestones")) {
            event.setCancelled(true);
        }
    }
}
