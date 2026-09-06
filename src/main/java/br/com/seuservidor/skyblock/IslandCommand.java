package br.com.seuservidor.skyblock;

import org.bukkit.command.*;
import org.bukkit.entity.Player;
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
            case "home", "casa" -> islands.get(player.getUniqueId()).ifPresentOrElse(
                island -> { player.teleport(islands.home(island)); player.sendMessage("§aWelcome to your island."); },
                () -> player.sendMessage("§cYou don't have an island yet. Use §e/island create§c."));
            case "info" -> islands.get(player.getUniqueId()).ifPresentOrElse(
                island -> player.sendMessage("§aYour island is at §f" + island.centerX() + ", " + island.centerZ() + " §aand has a radius of §f" + islands.radius() + "§a blocks."),
                () -> player.sendMessage("§cYou don't have an island yet."));
            default -> player.sendMessage("§eUse: /island <create|home|info>");
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("create", "home", "info") : List.of();
    }
}
