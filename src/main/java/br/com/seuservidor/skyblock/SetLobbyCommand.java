package br.com.seuservidor.skyblock;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetLobbyCommand implements CommandExecutor {
    private final LobbyManager lobby;

    public SetLobbyCommand(LobbyManager lobby) {
        this.lobby = lobby;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        
        if (!player.hasPermission("skyblock.admin")) {
            player.sendMessage("§cNo permission.");
            return true;
        }

        lobby.setSpawn(player.getLocation());
        player.sendMessage("§aLobby spawn location set perfectly to your current position!");
        return true;
    }
}
