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

    private static final DecimalFormat[] FORMATTERS = {
        new DecimalFormat("#,##0.#"),
        new DecimalFormat("#,##0.#K"),
        new DecimalFormat("#,##0.#M"),
        new DecimalFormat("#,##0.#B"),
        new DecimalFormat("#,##0.#T")
    };

    public PlayerSessionListener(IslandManager islands, LobbyManager lobby, EconomyManager economy, GeneratorManager generators, MilestoneManager milestones) {
        this.islands = islands;
        this.lobby = lobby;
        this.economy = economy;
        this.generators = generators;
        this.milestones = milestones;
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
            Objective obj = board.registerNewObjective("skyblock", "dummy", net.kyori.adventure.text.Component.text("§b§lAETHER"));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            
            try { obj.numberFormat(NumberFormat.blank()); } catch (Throwable ignored) { }
            
            UUID uuid = player.getUniqueId();
            long balance = economy.getBalance(uuid);
            double moneyPerSecond = economy.getMoneyPerSecond(uuid);
            long moneyPerHour = economy.getMoneyPerHour(uuid);
            int gensCount = generators.getCount(uuid);
            int maxGens = milestones != null ? milestones.getGeneratorLimit(uuid) : 10;
            int maxWorkers = milestones != null ? milestones.getWorkerLimit(uuid) : 1;
            int playerLevel = milestones != null ? milestones.getOverallLevel(player) : 1;
            
            obj.getScore("§0  §f§lSTATISTICS").setScore(8);
            obj.getScore("§1§7 Balance: §f$" + formatValue(balance)).setScore(7);
            obj.getScore("§2§7 Money/s: §a$" + formatValue(moneyPerSecond)).setScore(6);
            obj.getScore("§3§7 Money/h: §a$" + formatValue(moneyPerHour)).setScore(5);
            obj.getScore("§4").setScore(4);
            obj.getScore("§5  §f§lPLAYER").setScore(3);
            obj.getScore("§6§7 Level: §f" + playerLevel).setScore(2);
            obj.getScore("§7§7 Generators: §f" + gensCount + "/" + maxGens).setScore(1);
            obj.getScore("§8§7 Workers: §f" + maxWorkers).setScore(0);
            
            player.setScoreboard(board);
        }
    }
}
