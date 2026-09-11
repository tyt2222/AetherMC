package br.com.seuservidor.skyblock;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RankManager implements CommandExecutor, TabCompleter, Listener {
    private static final TextColor FOOTER_MUTED = TextColor.fromHexString("#AAB7C4");
    private static final TextColor FOOTER_ACCENT = TextColor.fromHexString("#55FFFF");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final SkyblockPlugin plugin;
    private final File dataFile;
    private final Map<UUID, Rank> ranks = new ConcurrentHashMap<>();

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
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return rankNames();
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
            updateTabList(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                applyRank(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Component name = rankedName(event.getPlayer());
        event.renderer(ChatRenderer.viewerUnaware((source, sourceDisplayName, message) ->
            Component.empty()
                .append(name)
                .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                .append(message.colorIfAbsent(NamedTextColor.WHITE))
        ));
    }

    public Rank getRank(Player player) {
        return ranks.getOrDefault(player.getUniqueId(), Rank.MEMBER);
    }

    public void applyRank(Player player) {
        boolean canFly = getRank(player) != Rank.MEMBER;
        player.setAllowFlight(canFly);
        if (!canFly) {
            player.setFlying(false);
        }
        Component name = rankedName(player);
        player.playerListName(name);
        player.displayName(name);
        player.customName(name);
        updateTabList(player);
    }

    public void updateAllTabLists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyRank(player);
        }
    }

    private Component rankedName(Player player) {
        Rank rank = getRank(player);
        Component name = Component.empty()
            .append(LEGACY.deserialize(nameColor(rank) + player.getName()))
            .decoration(TextDecoration.ITALIC, false);
        if (rank == Rank.MEMBER) {
            return name;
        }
        return Component.empty()
            .append(rankPrefix(rank))
            .append(Component.text(" "))
            .append(name);
    }

    private Component rankPrefix(Rank rank) {
        String glyph = rankGlyph(rank);
        if (glyph != null) {
            return Component.text(glyph).color(NamedTextColor.WHITE);
        }
        return LEGACY.deserialize(prefix(rank));
    }

    private void updateTabList(Player player) {
        Component header = Component.empty()
            .append(Component.text("\n\n\n"))
            .append(Component.text("\uE238").color(NamedTextColor.WHITE))
            .append(Component.text("\n\n\n"));

        Component footer = Component.text("\nConnected to ")
            .color(FOOTER_MUTED)
            .append(Component.text("AetherMC")
                .color(FOOTER_ACCENT)
                .decorate(TextDecoration.BOLD))
            .append(Component.text("  |  " + Bukkit.getOnlinePlayers().size() + " online\n")
                .color(FOOTER_MUTED));

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
        YamlConfiguration data = DataFileUtil.load(dataFile);
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
            DataFileUtil.save(data, dataFile);
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
