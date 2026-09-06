package br.com.seuservidor.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.key.Key;
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
        Component logo = Component.text("\n\n\n\uE238\n\n\n")
                .font(Key.key("aethermc", "logo"));
        Component header = Component.empty()
                .append(logo)
                .append(Component.text("\n")
                    .font(Key.key("minecraft", "default")));

        Component footer = Component.text("\nConnected to ")
                .font(Key.key("minecraft", "default"))
                .color(TextColor.fromHexString("#AAB7C4"))
            .append(Component.text("AetherMC")
                .font(Key.key("minecraft", "default"))
                .color(TextColor.fromHexString("#55FFFF"))
                .decorate(TextDecoration.BOLD))
            .append(Component.text("  |  " + Bukkit.getOnlinePlayers().size() + " online\n")
                .font(Key.key("minecraft", "default"))
                .color(TextColor.fromHexString("#AAB7C4")));

        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Rank parseRank(String input) {
        String normalized = input.toUpperCase(Locale.ROOT).replace("-", "_");
        return switch (normalized) {
            case "MEMBER" -> Rank.MEMBER;
            case "SKYFARER" -> Rank.VIP;
            case "SKYFARER+", "SKYFARER_PLUS", "SKYFARERPLUS" -> Rank.VIP_PLUS;
            case "AETHERLORD" -> Rank.MVP;
            case "HELPER" -> Rank.HELPER;
            case "OWNER" -> Rank.OWNER;
            default -> null;
        };
    }

    private String prefix(Rank rank) {
        String configured = plugin.getConfig().getString("ranks." + rank.name() + ".prefix", rank.defaultPrefix());
        return ChatColor.translateAlternateColorCodes('&', configured);
    }

    private String rankLabel(Rank rank) {
        return switch (rank) {
            case VIP -> "SKYFARER";
            case VIP_PLUS -> "SKYFARER+";
            case MVP -> "AETHERLORD";
            default -> rank.name();
        };
    }

    private String displayName(Rank rank) {
        return rankLabel(rank);
    }

    private List<String> rankNames() {
        return List.of("member", "skyfarer", "skyfarer+", "aetherlord", "helper", "owner");
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
        VIP("&a[SKYFARER]", 499),
        VIP_PLUS("&b[SKYFARER+]", 999),
        MVP("&d[AETHERLORD]", 1999),
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
