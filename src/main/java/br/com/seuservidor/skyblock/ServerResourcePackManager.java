package br.com.seuservidor.skyblock;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

public final class ServerResourcePackManager implements Listener {
    private static final String DEFAULT_PACK_URL =
        "https://raw.githubusercontent.com/tyt2222/AetherMC/main/resource-pack/AetherMC-resource-pack.zip?v=3";
    private static final String DEFAULT_PACK_SHA1 = "1e83793b6bb457c9be42f4606919e4ec8982dc7f";
    private final SkyblockPlugin plugin;
    private final String url;
    private final byte[] sha1;
    private final boolean required;
    private final Component prompt;

    public ServerResourcePackManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        String configuredUrl = plugin.getConfig().getString("resource-pack.url", "");
        String configuredSha1 = plugin.getConfig().getString("resource-pack.sha1", "");
        this.url = configuredUrl == null || configuredUrl.isBlank() ? DEFAULT_PACK_URL : configuredUrl;
        this.sha1 = decodeSha1(configuredSha1 == null || configuredSha1.isBlank() ? DEFAULT_PACK_SHA1 : configuredSha1);
        this.required = plugin.getConfig().getBoolean("resource-pack.required", true);
        this.prompt = Component.text(plugin.getConfig().getString("resource-pack.prompt", "AetherMC requires its server resource pack."));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (url == null || url.isBlank() || sha1.length != 20) {
            plugin.getLogger().warning("Resource pack was not sent to " + player.getName()
                + ": invalid URL or SHA-1.");
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setResourcePack(url, sha1, prompt, required);
            }
        }, 20L);
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!required || url == null || url.isBlank() || sha1.length != 20) return;
        Player player = event.getPlayer();
        plugin.getLogger().info("Resource pack status for " + player.getName() + ": " + event.getStatus());
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> { }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                player.kick(Component.text("This server requires the AetherMC resource pack."));
            default -> { }
        }
    }

    private byte[] decodeSha1(String value) {
        if (value == null) return new byte[0];
        String clean = value.replace(" ", "").toLowerCase();
        if (clean.length() != 40) return new byte[0];
        byte[] bytes = new byte[20];
        try {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        } catch (NumberFormatException ignored) {
            return new byte[0];
        }
    }
}
