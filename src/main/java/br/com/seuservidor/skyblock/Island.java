package br.com.seuservidor.skyblock;

import org.bukkit.Location;
import java.util.UUID;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class Island {
    private final UUID owner;
    private final int centerX;
    private final int centerZ;
    private final Set<UUID> members = new HashSet<>();

    public Island(UUID owner, int centerX, int centerZ) {
        this.owner = owner;
        this.centerX = centerX;
        this.centerZ = centerZ;
    }

    public UUID owner() { return owner; }
    public int centerX() { return centerX; }
    public int centerZ() { return centerZ; }
    public Set<UUID> members() { return Collections.unmodifiableSet(members); }
    public boolean hasMember(UUID uuid) { return owner.equals(uuid) || members.contains(uuid); }
    public void addMember(UUID uuid) { if (!owner.equals(uuid)) members.add(uuid); }
    public void removeMember(UUID uuid) { members.remove(uuid); }

    public boolean contains(Location location, int radius) {
        return Math.abs(location.getBlockX() - centerX) <= radius
                && Math.abs(location.getBlockZ() - centerZ) <= radius;
    }
}
