package br.com.seuservidor.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

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
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            applyRank(event.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Rank rank = getRank(event.getPlayer());
        event.setFormat("%1$s§8: §f%2$s");
    }

    public Rank getRank(Player player) {
        return ranks.getOrDefault(player.getUniqueId(), Rank.MEMBER);
    }

    public void applyRank(Player player) {
        Rank rank = getRank(player);
        Component name = rankGlyph(rank) == null
                ? LegacyComponentSerializer.legacySection().deserialize(
                    prefix(rank) + " " + nameColor(rank) + player.getName()
                ).font(Key.key("minecraft", "default"))
                : Component.empty()
                    .append(Component.text(rankGlyph(rank))
                        .font(Key.key("aethermc", "ranks")))
                    .append(Component.text(" ")
                        .font(Key.key("minecraft", "default")))
                    .append(LegacyComponentSerializer.legacySection().deserialize(
                        nameColor(rank) + player.getName()
                    ).font(Key.key("minecraft", "default")));
        player.playerListName(name);
        player.displayName(name);
        updateTabList(player);
    }

    public void updateAllTabLists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRank(player);
        }
    }

    private void updateTabList(Player player) {
        Component header = Component.empty()
                .append(Component.text("\n\n\n")
                    .font(Key.key("minecraft", "default")))
                .append(Component.text("\uE238")
                    .font(Key.key("aethermc", "logo")))
                .append(Component.text("\n\n\n")
                    .font(Key.key("minecraft", "default")))
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
            case "SKYBOUND", "SKYFARER+", "SKYFARER_PLUS", "SKYFARERPLUS" -> Rank.VIP_PLUS;
            case "CELESTIAL", "AETHERLORD" -> Rank.MVP;
            case "HELPER" -> Rank.HELPER;
            case "OWNER" -> Rank.OWNER;
            case "ADMIN" -> Rank.ADMIN;
            default -> null;
        };
    }

    private String prefix(Rank rank) {
        String glyph = rankGlyph(rank);
        if (glyph != null) return glyph;

        String configured = plugin.getConfig().getString("ranks." + rank.name() + ".prefix", rank.defaultPrefix());
        if (rank == Rank.MVP && configured.contains("AETHERLORD")) {
            configured = configured.replace("AETHERLORD", "CELESTIAL");
        }
        if (rank == Rank.VIP_PLUS && configured.contains("SKYFARER")) {
            configured = configured.replace("SKYFARER+", "SKYBOUND");
        }
        return ChatColor.translateAlternateColorCodes('&', configured);
    }

    private String rankGlyph(Rank rank) {
        return switch (rank) {
            case OWNER -> "\uE800";
            case ADMIN -> "\uE801";
            case HELPER -> "\uE802";
            case VIP_PLUS -> "\uE803";
            case MVP -> "\uE804";
            case VIP -> "\uE805";
            default -> null;
        };
    }

    private String nameColor(Rank rank) {
        String configured = plugin.getConfig().getString("ranks." + rank.name() + ".name-color");
        return configured == null ? rank.nameColor() : ChatColor.translateAlternateColorCodes('&', configured);
    }

    private String rankLabel(Rank rank) {
        return switch (rank) {
            case VIP -> "SKYFARER";
            case VIP_PLUS -> "SKYBOUND";
            case MVP -> "CELESTIAL";
            default -> rank.name();
        };
    }

    private String displayName(Rank rank) {
        return rankLabel(rank);
    }

    private List<String> rankNames() {
        return List.of("member", "skyfarer", "skybound", "celestial", "helper", "admin", "owner");
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

    public void resetPlayer(UUID uuid) {
        if (ranks.remove(uuid) != null) save();
    }

    public enum Rank {
        MEMBER("§7[MEMBER]", "§7", 0),
        VIP("&a[SKYFARER]", "§a", 499),
        VIP_PLUS("&b[SKYBOUND]", "§b", 999),
        MVP("&d[AETHERLORD]", "§d", 1999),
        HELPER("&2[HELPER]", "§2", 0),
        OWNER("&4[OWNER]", "§4", 0),
        ADMIN("&c[ADMIN]", "§c", 0);

        private final String defaultPrefix;
        private final String nameColor;
        private final int suggestedPrice;

        Rank(String defaultPrefix, String nameColor, int suggestedPrice) {
            this.defaultPrefix = defaultPrefix;
            this.nameColor = nameColor;
            this.suggestedPrice = suggestedPrice;
        }

        public String defaultPrefix() { return defaultPrefix; }
        public String nameColor() { return nameColor; }
        public int suggestedPrice() { return suggestedPrice; }
    }
}
