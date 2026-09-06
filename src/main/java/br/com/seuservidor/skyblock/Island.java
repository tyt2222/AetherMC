package br.com.seuservidor.skyblock;

import org.bukkit.Location;
import java.util.UUID;

public record Island(UUID owner, int centerX, int centerZ) {
    public boolean contains(Location location, int radius) {
        return Math.abs(location.getBlockX() - centerX) <= radius
                && Math.abs(location.getBlockZ() - centerZ) <= radius;
    }
}
