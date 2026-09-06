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
    public Optional<Island> getForPlayer(UUID player) {
        Optional<Island> coop = byOwner.values().stream()
            .filter(island -> island.members().contains(player)).findFirst();
        return coop.isPresent() ? coop : Optional.ofNullable(byOwner.get(player));
    }
    public boolean isMember(UUID player, UUID owner) {
        Island island = byOwner.get(owner);
        return island != null && island.hasMember(player);
    }
    public boolean owns(Player player, Location location) {
        return getForPlayer(player.getUniqueId()).map(island -> island.contains(location, radius)).orElse(false);
    }
    public boolean isSkyblockWorld(Location location) { return location.getWorld().equals(world); }
    public int radius() { return radius; }

    public Island create(Player player) {
        Island existing = byOwner.get(player.getUniqueId());
        if (existing != null) return existing;
        int index = nextIsland++;
        int ring = (int) Math.ceil((Math.sqrt(index + 1) - 1) / 2);
        int side = ring * 2 + 1;
        int offset = index - (side - 2) * (side - 2);
        int x;
        int z;
        if (ring == 0) {
            x = 0;
            z = 0;
        } else if (offset < side - 1) {
            x = -ring + offset;
            z = -ring;
        } else if (offset < 2 * (side - 1)) {
            x = ring;
            z = -ring + (offset - (side - 1));
        } else if (offset < 3 * (side - 1)) {
            x = ring - (offset - 2 * (side - 1));
            z = ring;
        } else {
            x = -ring;
            z = ring - (offset - 3 * (side - 1));
        }
        Island island = new Island(player.getUniqueId(), x * spacing, z * spacing);
        byOwner.put(player.getUniqueId(), island);
        buildStarterIsland(island);
        save();
        return island;
    }

    public void deleteIsland(UUID owner) {
        byOwner.remove(owner);
        save();
    }

    public Island resetIsland(UUID owner) {
        Island existing = byOwner.get(owner);
        if (existing == null) return null;

        clearIsland(existing);
        Island reset = new Island(owner, existing.centerX(), existing.centerZ());
        byOwner.put(owner, reset);
        buildStarterIsland(reset);
        save();
        return reset;
    }

    private void clearIsland(Island island) {
        int minX = island.centerX() - radius;
        int maxX = island.centerX() + radius;
        int minZ = island.centerZ() - radius;
        int maxZ = island.centerZ() + radius;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
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
            Island island = new Island(owner, data.getInt("islands." + id + ".x"), data.getInt("islands." + id + ".z"));
            for (String member : data.getStringList("islands." + id + ".members")) {
                try { island.addMember(UUID.fromString(member)); } catch (IllegalArgumentException ignored) { }
            }
            byOwner.put(owner, island);
        }
    }
    public void save() {
        YamlConfiguration data = new YamlConfiguration(); data.set("next-island", nextIsland);
        byOwner.forEach((id, island) -> {
            data.set("islands." + id + ".x", island.centerX());
            data.set("islands." + id + ".z", island.centerZ());
            data.set("islands." + id + ".members", island.members().stream().map(UUID::toString).toList());
        });
        try { data.save(dataFile); } catch (IOException e) { plugin.getLogger().severe("Falha ao salvar ilhas: " + e.getMessage()); }
    }

    public boolean invite(UUID owner, UUID member) {
        Island island = byOwner.get(owner);
        if (island == null || getCoopIsland(member).isPresent() || owner.equals(member)) return false;
        island.addMember(member);
        save();
        return true;
    }

    public boolean removeMember(UUID owner, UUID member) {
        Island island = byOwner.get(owner);
        if (island == null || !island.members().contains(member)) return false;
        island.removeMember(member);
        save();
        return true;
    }

    public Optional<Island> getCoopIsland(UUID player) {
        return byOwner.values().stream().filter(island -> island.members().contains(player)).findFirst();
    }
}
