package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;

public final class GeneratorCommand implements CommandExecutor, TabCompleter {
    private final GeneratorManager generators;
    public GeneratorCommand(GeneratorManager generators) { this.generators = generators; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skyblock.admin")) { sender.sendMessage("§cNo permission."); return true; }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) { sender.sendMessage("§eUse: /generator give <player> <type> [amount]"); return true; }
        Player target = Bukkit.getPlayerExact(args[1]); if (target == null) { sender.sendMessage("§cPlayer not found."); return true; }
        var item = generators.createItem(args[2].toLowerCase()); if (item == null) { sender.sendMessage("§cInvalid type. Options: " + String.join(", ", generators.typeIds())); return true; }
        
        int amount = 1;
        if (args.length >= 4) {
            try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
        }
        item.setAmount(amount);
        
        target.getInventory().addItem(item); sender.sendMessage("§a" + amount + "x Generator given to " + target.getName() + "."); target.sendMessage("§aYou received " + amount + "x generators!"); return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("give");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return new ArrayList<>(generators.typeIds());
        return List.of();
    }
}
