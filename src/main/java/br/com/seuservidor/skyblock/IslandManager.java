package br.com.seuservidor.skyblock;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.io.IOException;
import java.util.*;

public final class IslandManager {
    private final SkyblockPlugin plugin;
    private final World world;
    private final int spacing;
    private final int radius;
    private final Map<UUID, Island> byOwner = new HashMap<>();
    private final File dataFile;
    private int nextIsland;

    public IslandManager(SkyblockPlugin plugin, World world) {
        this.plugin = plugin; this.world = world;
        spacing = plugin.getConfig().getInt("island-spacing", 400);
        radius = plugin.getConfig().getInt("island-radius", 100);
        dataFile = new File(plugin.getDataFolder(), "islands.yml");
        load();
    }
    public Optional<Island> get(UUID owner) { return Optional.ofNullable(byOwner.get(owner)); }
    public Collection<Island> getAll() { return byOwner.values(); }
    public boolean owns(Player player, Location location) {
        return get(player.getUniqueId()).map(island -> island.contains(location, radius)).orElse(false);
    }
    public boolean isSkyblockWorld(Location location) { return location.getWorld().equals(world); }
    public int radius() { return radius; }

    public Island create(Player player) {
        Island existing = byOwner.get(player.getUniqueId());
        if (existing != null) return existing;
        int index = nextIsland++;
        int side = Math.max(1, (int) Math.ceil(Math.sqrt(nextIsland)));
        int x = ((index % side) - side / 2) * spacing;
        int z = ((index / side) - side / 2) * spacing;
        Island island = new Island(player.getUniqueId(), x, z);
        byOwner.put(player.getUniqueId(), island);
        buildStarterIsland(island);
        save();
        return island;
    }

    public void deleteIsland(UUID owner) {
        byOwner.remove(owner);
        save();
    }

    private void buildStarterIsland(Island island) {
        int y = 100;
        // 10x10 grass square, without tree and without chest
        for (int x = -5; x < 5; x++) {
            for (int z = -5; z < 5; z++) {
                world.getBlockAt(island.centerX() + x, y, island.centerZ() + z).setType(Material.GRASS_BLOCK, false);
                world.getBlockAt(island.centerX() + x, y - 1, island.centerZ() + z).setType(Material.DIRT, false);
                world.getBlockAt(island.centerX() + x, y - 2, island.centerZ() + z).setType(Material.DIRT, false);
            }
        }
    }

    public Location home(Island island) { return new Location(world, island.centerX() + .5, 102, island.centerZ() + .5); }

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        nextIsland = data.getInt("next-island", 0);
        for (String id : data.getConfigurationSection("islands") == null ? Set.<String>of() : data.getConfigurationSection("islands").getKeys(false)) {
            UUID owner = UUID.fromString(id);
            byOwner.put(owner, new Island(owner, data.getInt("islands." + id + ".x"), data.getInt("islands." + id + ".z")));
        }
    }
    public void save() {
        YamlConfiguration data = new YamlConfiguration(); data.set("next-island", nextIsland);
        byOwner.forEach((id, island) -> { data.set("islands." + id + ".x", island.centerX()); data.set("islands." + id + ".z", island.centerZ()); });
        try { data.save(dataFile); } catch (IOException e) { plugin.getLogger().severe("Falha ao salvar ilhas: " + e.getMessage()); }
    }
}
