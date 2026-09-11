package br.com.seuservidor.skyblock;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class EconomyManager {
    private final SkyblockPlugin plugin;
    private final File dataFile;
    private final YamlConfiguration data;
    private final HashMap<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, Deque<Earning>> recentEarnings = new HashMap<>();
    private boolean dirty;

    public EconomyManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "economy.yml");
        this.data = DataFileUtil.load(dataFile);
        
        if (data.contains("balances")) {
            for (String key : data.getConfigurationSection("balances").getKeys(false)) {
                balances.put(UUID.fromString(key), data.getLong("balances." + key));
            }
        }
    }

    public long getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0L);
    }

    public void addBalance(UUID uuid, long amount) {
        balances.put(uuid, getBalance(uuid) + amount);
        recordEarning(uuid, amount);
        dirty = true;
    }

    public boolean removeBalance(UUID uuid, long amount) {
        if (getBalance(uuid) >= amount) {
            balances.put(uuid, getBalance(uuid) - amount);
            dirty = true;
            return true;
        }
        return false;
    }

    public void saveIfDirty() {
        if (dirty) save();
    }

    public void save() {
        for (UUID uuid : balances.keySet()) {
            data.set("balances." + uuid.toString(), balances.get(uuid));
        }
        try {
            DataFileUtil.save(data, dataFile);
            dirty = false;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public double getMoneyPerSecond(UUID uuid) {
        pruneEarnings(uuid, System.currentTimeMillis());
        return recentEarnings.getOrDefault(uuid, new ArrayDeque<>()).stream().mapToLong(Earning::amount).sum() / 3600.0;
    }

    public long getMoneyPerHour(UUID uuid) {
        pruneEarnings(uuid, System.currentTimeMillis());
        return recentEarnings.getOrDefault(uuid, new ArrayDeque<>()).stream().mapToLong(Earning::amount).sum();
    }

    private void recordEarning(UUID uuid, long amount) {
        if (amount <= 0) return;
        long now = System.currentTimeMillis();
        Deque<Earning> earnings = recentEarnings.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        earnings.addLast(new Earning(now, amount));
        pruneEarnings(uuid, now);
    }

    private void pruneEarnings(UUID uuid, long now) {
        Deque<Earning> earnings = recentEarnings.get(uuid);
        if (earnings == null) return;
        long cutoff = now - 3_600_000L;
        for (Iterator<Earning> it = earnings.iterator(); it.hasNext();) {
            if (it.next().time() >= cutoff) break;
            it.remove();
        }
    }

    private record Earning(long time, long amount) { }
}
