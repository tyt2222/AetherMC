package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.*;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.text.DecimalFormat;
import java.util.UUID;

public final class PlayerSessionListener implements Listener {
    private final IslandManager islands;
    private final LobbyManager lobby;
    private final EconomyManager economy;
    private final GeneratorManager generators;
    private final MilestoneManager milestones;
    private final MinionManager minions;

    private static final DecimalFormat[] FORMATTERS = {
        new DecimalFormat("#,##0.#"),
        new DecimalFormat("#,##0.#K"),
        new DecimalFormat("#,##0.#M"),
        new DecimalFormat("#,##0.#B"),
        new DecimalFormat("#,##0.#T")
    };

    public PlayerSessionListener(IslandManager islands, LobbyManager lobby, EconomyManager economy, GeneratorManager generators, MilestoneManager milestones, MinionManager minions) {
        this.islands = islands;
        this.lobby = lobby;
        this.economy = economy;
        this.generators = generators;
        this.milestones = milestones;
        this.minions = minions;
    }

    public static String formatValue(double value) {
        if (value < 1000) return String.valueOf((int) value);
        int exp = (int) (Math.log(value) / Math.log(1000));
        if (exp >= FORMATTERS.length) exp = FORMATTERS.length - 1;
        return FORMATTERS[exp].format(value / Math.pow(1000, exp));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.teleport(lobby.getSpawn());
        
        if (islands.get(player.getUniqueId()).isEmpty()) {
            islands.create(player);
        }
        
        updateScoreboard(player);
        player.setLevel(1);
        player.setExp(0f);
    }

    public void updateScoreboard(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            Scoreboard board = manager.getNewScoreboard();
            Objective obj = board.registerNewObjective("skyblock", "dummy", net.kyori.adventure.text.Component.text("§b§lAETHERMC"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            try { obj.numberFormat(NumberFormat.blank()); } catch (Throwable ignored) { }
            
            UUID uuid = player.getUniqueId();
            long balance = economy.getBalance(uuid);
            UUID islandOwner = islands.getForPlayer(uuid).map(Island::owner).orElse(uuid);
            long moneyPerHour = generators.getMoneyPerHour(islandOwner);
            int maxGens = milestones != null ? milestones.getGeneratorLimit(islandOwner) : 10;
            int maxWorkers = milestones != null ? milestones.getWorkerLimit(islandOwner) : 1;
            int workers = minions != null ? minions.getCount(islandOwner) : 0;
            int playerLevel = 1;
            
            obj.getScore("§f\u00A0§r").setScore(12);
            obj.getScore("§1§b§l✦ ᴇᴄᴏɴᴏᴍʏ").setScore(11);
            obj.getScore("§2§8 ").setScore(10);
            obj.getScore("§3§7Balance §f$" + formatValue(balance)).setScore(9);
            obj.getScore("§4§7Income §a$" + formatValue(moneyPerHour) + "§7/h").setScore(8);
            obj.getScore("§5§8 ").setScore(7);
            obj.getScore("§6§d§l✦ ɪꜱʟᴀɴᴅ").setScore(6);
            obj.getScore("§7§8 ").setScore(5);
            obj.getScore("§8§7Level §f" + playerLevel).setScore(4);
            obj.getScore("§9§7Generators §f" + generators.getCount(islandOwner) + "§8/§f" + maxGens).setScore(3);
            obj.getScore("§a§7Workers §f" + workers + "§8/§f" + maxWorkers).setScore(2);
            obj.getScore("§b§8 ").setScore(1);
            obj.getScore("§c§7ᴘʟᴀʏ.ᴀᴇᴛʜᴇʀᴍᴄ.ᴄᴏᴍ").setScore(0);
            
            player.setScoreboard(board);
        }
    }
}
