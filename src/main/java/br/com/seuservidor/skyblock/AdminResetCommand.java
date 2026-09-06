package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class AdminResetCommand implements CommandExecutor {
    private final IslandManager islands;
    private final EconomyManager economy;
    private final LobbyManager lobby;
    private final GeneratorManager generators;
    private final PlayerSessionListener sessionListener;
    private final MilestoneManager milestones;
    private final MinionManager minions;
    private final AuctionManager auctions;
    private final RankManager ranks;

    public AdminResetCommand(IslandManager islands, EconomyManager economy, LobbyManager lobby, GeneratorManager generators, PlayerSessionListener sessionListener, MilestoneManager milestones, MinionManager minions, AuctionManager auctions, RankManager ranks) {
        this.islands = islands;
        this.economy = economy;
        this.lobby = lobby;
        this.generators = generators;
        this.sessionListener = sessionListener;
        this.milestones = milestones;
        this.minions = minions;
        this.auctions = auctions;
        this.ranks = ranks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /adminreset <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or offline.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        
        // 1. Reset Economy
        economy.removeBalance(uuid, economy.getBalance(uuid));
        
        // 2. Clear Inventory
        target.getInventory().clear();
        
        // 3. Teleport to Lobby
        target.teleport(lobby.getSpawn());
        
        // 4. Reset Generators and Minions before clearing the island blocks.
        generators.resetPlayer(uuid);
        minions.resetPlayer(uuid);

        // 5. Reset the physical island while keeping its original coordinates.
        Island resetIsland = islands.resetIsland(uuid);
        if (resetIsland == null) {
            resetIsland = islands.create(target);
        }

        // 6. Reset Milestones
        milestones.resetPlayer(uuid);
        auctions.removeListings(uuid);
        ranks.resetPlayer(uuid);
        ranks.applyRank(target);
        
        target.sendMessage("§c§lYour progress has been completely reset by an admin!");
        sender.sendMessage("§aProgress for " + target.getName() + " has been reset.");
        
        sessionListener.updateScoreboard(target);
        
        return true;
    }
}
