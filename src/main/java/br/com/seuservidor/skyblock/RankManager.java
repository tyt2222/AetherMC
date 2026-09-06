package br.com.seuservidor.skyblock;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class RankManager implements CommandExecutor, TabCompleter, Listener {
    private final SkyblockPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Rank> ranks = new HashMap<>();

    public RankManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ranks.yml");
        load();
        plugin.getCommand("rank").setExecutor(this);
        plugin.getCommand("rank").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllTabLists, 20L, 200L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 3 || !args[0].equalsIgnoreCase("set")) {
            sender.sendMessage("§eUse: /rank set <player> <rank>");
            sender.sendMessage("§7Ranks: " + String.join(", ", rankNames()));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        Rank rank = parseRank(args[2]);
        if (rank == null) {
            sender.sendMessage("§cInvalid rank. Options: " + String.join(", ", rankNames()));
            return true;
        }

        ranks.put(target.getUniqueId(), rank);
        save();
        applyRank(target);
        sender.sendMessage("§aSet " + target.getName() + "'s rank to " + displayName(rank) + "§a.");
        target.sendMessage("§aYour rank is now " + displayName(rank) + "§a.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("set");
        if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3) return rankNames();
        return List.of();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        applyRank(event.getPlayer());
        updateAllTabLists();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Rank rank = getRank(event.getPlayer());
        event.setFormat(prefix(rank) + " §7%1$s§8: §f%2$s");
    }

    public Rank getRank(Player player) {
        return ranks.getOrDefault(player.getUniqueId(), Rank.MEMBER);
    }

    public void applyRank(Player player) {
        Rank rank = getRank(player);
        player.setPlayerListName(prefix(rank) + " §f" + player.getName());
        player.setDisplayName(prefix(rank) + " §f" + player.getName());
        updateTabList(player);
    }

    public void updateAllTabLists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRank(player);
        }
    }

    private void updateTabList(Player player) {
        String header = "\n"
            + "§f\uE000\n"
            + "§x§5§5§F§F§F§F§lAETHERMC\n"
            + "§7Skyblock Generators\n";
        String footer = "\n"
            + "§7You are connected to §bAetherMC §8| §f" + Bukkit.getOnlinePlayers().size() + " players online\n"
            + "§7Ranks: §aVIP §8- §bVIP+ §8- §dMVP §8| §fUse §e/shop §for §e/market\n";
        player.setPlayerListHeaderFooter(header, footer);
    }

    private Rank parseRank(String input) {
        String normalized = input.toUpperCase(Locale.ROOT).replace("-", "_");
        for (Rank rank : Rank.values()) {
            if (rank.name().equals(normalized)) return rank;
        }
        return null;
    }

    private String prefix(Rank rank) {
        String configured = plugin.getConfig().getString("ranks." + rank.name() + ".prefix", rank.defaultPrefix());
        return ChatColor.translateAlternateColorCodes('&', configured);
    }

    private String displayName(Rank rank) {
        return prefix(rank).replace("[", "").replace("]", "");
    }

    private List<String> rankNames() {
        return Arrays.stream(Rank.values()).map(Enum::name).map(String::toLowerCase).toList();
    }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                ranks.put(UUID.fromString(key), Rank.valueOf(data.getString(key, "MEMBER")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        for (Map.Entry<UUID, Rank> entry : ranks.entrySet()) {
            data.set(entry.getKey().toString(), entry.getValue().name());
        }

        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save ranks: " + e.getMessage());
        }
    }

    public enum Rank {
        MEMBER("§7[MEMBER]", 0),
        VIP("&a[VIP]", 499),
        VIP_PLUS("&b[VIP+]", 999),
        MVP("&d[MVP]", 1999),
        HELPER("&2[HELPER]", 0),
        OWNER("&4[OWNER]", 0);

        private final String defaultPrefix;
        private final int suggestedPrice;

        Rank(String defaultPrefix, int suggestedPrice) {
            this.defaultPrefix = defaultPrefix;
            this.suggestedPrice = suggestedPrice;
        }

        public String defaultPrefix() { return defaultPrefix; }
        public int suggestedPrice() { return suggestedPrice; }
    }
}
