package br.com.seuservidor.skyblock;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class LobbyManager implements CommandExecutor {
    private final SkyblockPlugin plugin;
    private final World lobbyWorld;
    private Location spawnLocation;

    public LobbyManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.lobbyWorld = Bukkit.createWorld(new WorldCreator("lobby").generator(new SkyblockPlugin.VoidGenerator()));
        
        loadSpawn();

        plugin.getCommand("lobby").setExecutor(this);
    }
    
    public void loadSpawn() {
        if (plugin.getConfig().contains("lobby.x")) {
            double x = plugin.getConfig().getDouble("lobby.x");
            double y = plugin.getConfig().getDouble("lobby.y");
            double z = plugin.getConfig().getDouble("lobby.z");
            float yaw = (float) plugin.getConfig().getDouble("lobby.yaw");
            float pitch = (float) plugin.getConfig().getDouble("lobby.pitch");
            this.spawnLocation = new Location(lobbyWorld, x, y, z, yaw, pitch);
        } else {
            this.spawnLocation = new Location(lobbyWorld, 0.5, 100, 0.5);
            // Platforma básica de segurança caso o mapa seja novo e vazio
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (lobbyWorld.getBlockAt(x, 99, z).getType() == Material.AIR) {
                        lobbyWorld.getBlockAt(x, 99, z).setType(Material.BEDROCK);
                    }
                }
            }
        }
    }
    
    public void setSpawn(Location loc) {
        this.spawnLocation = loc;
        plugin.getConfig().set("lobby.x", loc.getX());
        plugin.getConfig().set("lobby.y", loc.getY());
        plugin.getConfig().set("lobby.z", loc.getZ());
        plugin.getConfig().set("lobby.yaw", loc.getYaw());
        plugin.getConfig().set("lobby.pitch", loc.getPitch());
        plugin.saveConfig();
    }

    public Location getSpawn() {
        return spawnLocation;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            player.teleport(spawnLocation);
            player.sendMessage("§aWelcome to the lobby!");
        } else {
            sender.sendMessage("§cThis command is for players only.");
        }
        return true;
    }
}
