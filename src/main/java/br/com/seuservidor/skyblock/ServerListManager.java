package br.com.seuservidor.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ServerListManager implements Listener {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private final SkyblockPlugin plugin;
    private CachedServerIcon icon;

    public ServerListManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.icon = loadIcon();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        applyServerPropertiesMotd();
    }

    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        event.motd(motd());
        if (icon != null) {
            event.setServerIcon(icon);
        }
    }

    private Component motd() {
        String line1 = color(plugin.getConfig().getString(
            "server-list.motd-line-1",
            "&b&lAetherMC &8| &fOFFICIAL SURVIVAL SERVER &7[1.20.4]"
        ));
        String line2 = color(plugin.getConfig().getString(
            "server-list.motd-line-2",
            "&aONLINE &8- &fJoin 500+ Players Now!"
        ));
        return LEGACY.deserialize(line1)
            .append(Component.newline())
            .append(LEGACY.deserialize(line2));
    }

    private void applyServerPropertiesMotd() {
        String line1 = color(plugin.getConfig().getString(
            "server-list.motd-line-1",
            "&b&lAetherMC &8| &fOFFICIAL SURVIVAL SERVER &7[1.20.4]"
        ));
        String line2 = color(plugin.getConfig().getString(
            "server-list.motd-line-2",
            "&aONLINE &8- &fJoin 500+ Players Now!"
        ));
        Bukkit.getServer().setMotd(line1 + "\n" + line2);
    }

    private CachedServerIcon loadIcon() {
        try {
            File serverRoot = plugin.getDataFolder().getParentFile().getParentFile();
            File iconFile = new File(serverRoot, "server-icon.png");
            File source = findSourceIcon(iconFile);
            if (source == null) return null;

            File prepared = prepareIcon(source, iconFile);
            return Bukkit.loadServerIcon(prepared);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load server-icon.png: " + e.getMessage());
            return null;
        }
    }

    private File findSourceIcon(File serverRootIcon) {
        File dataIcon = new File(plugin.getDataFolder(), "server-icon.png");
        if (dataIcon.isFile()) return dataIcon;
        if (serverRootIcon.isFile()) return serverRootIcon;
        try (InputStream in = plugin.getResource("server-icon.png")) {
            if (in == null) return null;
            plugin.getDataFolder().mkdirs();
            File extracted = new File(plugin.getDataFolder(), "server-icon.png");
            Files.copy(in, extracted.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return extracted;
        } catch (Exception e) {
            return serverRootIcon.isFile() ? serverRootIcon : null;
        }
    }

    private File prepareIcon(File source, File destination) throws Exception {
        BufferedImage image = ImageIO.read(source);
        if (image == null) return source;
        if (image.getWidth() == 64 && image.getHeight() == 64 && source.getCanonicalFile().equals(destination.getCanonicalFile())) {
            return destination;
        }

        BufferedImage scaled = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.drawImage(image, 0, 0, 64, 64, null);
        graphics.dispose();
        ImageIO.write(scaled, "png", destination);
        return destination;
    }

    private String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
