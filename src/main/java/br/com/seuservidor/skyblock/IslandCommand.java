package br.com.seuservidor.skyblock;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.List;

public final class IslandCommand implements CommandExecutor, TabCompleter {
    private final IslandManager islands;
    public IslandCommand(IslandManager islands) { this.islands = islands; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cThis command is for players only."); return true; }
        String action = args.length == 0 ? "home" : args[0].toLowerCase();
        switch (action) {
            case "create", "criar" -> {
                boolean newIsland = islands.get(player.getUniqueId()).isEmpty();
                Island island = islands.create(player);
                player.teleport(islands.home(island));
                player.sendMessage(newIsland ? "§aYour island was created! Use §e/island home §ato return." : "§eYou already had an island; teleporting you there.");
            }
            case "home", "casa" -> islands.getForPlayer(player.getUniqueId()).ifPresentOrElse(
                island -> { player.teleport(islands.home(island)); player.sendMessage("§aWelcome to your island."); },
                () -> player.sendMessage("§cYou don't have an island yet. Use §e/island create§c."));
            case "open", "abrir" -> {
                if (islands.setOpen(player.getUniqueId(), true)) player.sendMessage("§aYour island is now open to visitors.");
                else player.sendMessage("§cYou don't have an island yet.");
            }
            case "close", "fechar" -> {
                if (islands.setOpen(player.getUniqueId(), false)) player.sendMessage("§eYour island is now closed to visitors.");
                else player.sendMessage("§cYou don't have an island yet.");
            }
            case "visit", "visitar" -> {
                if (args.length != 2) { player.sendMessage("§eUse: /is visit <player>"); break; }
                Island target = islands.findByName(args[1]).orElse(null);
                if (target == null) { player.sendMessage("§cThat player does not have an island."); break; }
                if (!target.isOpen() && !target.hasMember(player.getUniqueId())) {
                    player.sendMessage("§cThat island is closed to visitors."); break;
                }
                player.teleport(islands.home(target));
                player.sendMessage("§aVisiting " + args[1] + "'s island.");
            }
            case "info" -> islands.getForPlayer(player.getUniqueId()).ifPresentOrElse(
                island -> player.sendMessage("§aYour island is at §f" + island.centerX() + ", " + island.centerZ() + " §aand has a radius of §f" + islands.radius() + "§a blocks."),
                () -> player.sendMessage("§cYou don't have an island yet."));
            case "invite" -> {
                if (args.length != 2) { player.sendMessage("§eUse: /is invite <player>"); break; }
                if (islands.get(player.getUniqueId()).isEmpty()) { player.sendMessage("§cOnly an island owner can invite players."); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { player.sendMessage("§cPlayer not found."); break; }
                if (islands.invite(player.getUniqueId(), target.getUniqueId())) {
                    target.teleport(islands.home(islands.get(player.getUniqueId()).orElseThrow()));
                    player.sendMessage("§a" + target.getName() + " joined your coop.");
                    target.sendMessage("§aYou joined " + player.getName() + "'s coop and were teleported to the island.");
                } else player.sendMessage("§cThat player already belongs to a coop or the invite is invalid.");
            }
            case "kick", "expel" -> {
                if (args.length != 2) { player.sendMessage("§eUse: /is kick <player>"); break; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null || !islands.removeMember(player.getUniqueId(), target.getUniqueId())) {
                    player.sendMessage("§cThat player is not a member of your coop."); break;
                }
                player.sendMessage("§aPlayer removed from your coop.");
                target.sendMessage("§cYou were removed from the coop. Your /is now leads to your original island.");
                target.teleport(islands.home(islands.get(target.getUniqueId()).orElseThrow()));
            }
            case "leave" -> {
                Island coop = islands.getCoopIsland(player.getUniqueId()).orElse(null);
                if (coop == null || !islands.removeMember(coop.owner(), player.getUniqueId())) {
                    player.sendMessage("§cYou are not a member of a coop."); break;
                }
                player.sendMessage("§aYou left the coop.");
                player.teleport(islands.home(islands.get(player.getUniqueId()).orElseThrow()));
            }
            case "members" -> islands.getForPlayer(player.getUniqueId()).ifPresentOrElse(
                island -> player.sendMessage("§aCoop owner: §f" + Bukkit.getOfflinePlayer(island.owner()).getName()
                    + " §7| Members: §f" + island.members().stream().map(uuid -> Bukkit.getOfflinePlayer(uuid).getName()).toList()),
                () -> player.sendMessage("§cYou don't have an island yet."));
            default -> player.sendMessage("§eUse: /island <create|home|open|close|visit|info|invite|kick|leave|members>");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("create", "home", "open", "close", "visit", "info", "invite", "kick", "leave", "members");
        if (args.length == 2 && (args[0].equalsIgnoreCase("visit") || args[0].equalsIgnoreCase("visitar"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        }
        return List.of();
    }
}
