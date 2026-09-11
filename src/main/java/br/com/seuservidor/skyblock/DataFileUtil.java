package br.com.seuservidor.skyblock;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

final class DataFileUtil {
    private DataFileUtil() { }

    static synchronized void save(YamlConfiguration data, File target) throws IOException {
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        File backup = new File(target.getParentFile(), target.getName() + ".bak");
        data.save(temporary);
        if (target.isFile()) {
            Files.copy(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static YamlConfiguration load(File target) {
        YamlConfiguration loaded = loadFile(target);
        if (target.isFile() && target.length() > 0 && loaded.getKeys(false).isEmpty()) {
            File backup = new File(target.getParentFile(), target.getName() + ".bak");
            YamlConfiguration restored = loadFile(backup);
            if (!restored.getKeys(false).isEmpty()) return restored;
        }
        return loaded;
    }

    private static YamlConfiguration loadFile(File file) {
        if (!file.isFile()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(file);
    }
}
