package br.com.seuservidor.skyblock;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;

public class EconomyListener implements Listener {
    private final GeneratorManager generators;
    private final EconomyManager economy;
    private final PlayerSessionListener sessionListener;
    private final MilestoneManager milestones;

    public EconomyListener(GeneratorManager generators, EconomyManager economy, PlayerSessionListener sessionListener, MilestoneManager milestones) {
        this.generators = generators;
        this.economy = economy;
        this.sessionListener = sessionListener;
        this.milestones = milestones;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        
        if (hand.hasItemMeta() && hand.getItemMeta().getPersistentDataContainer().has(generators.moneyKey, PersistentDataType.INTEGER)) {
            Integer value = hand.getItemMeta().getPersistentDataContainer().get(generators.moneyKey, PersistentDataType.INTEGER);
            if (value != null) {
                event.setCancelled(true);
                
                long totalAdded = 0;
                
                ItemStack[] contents = player.getInventory().getContents();
                for (int i = 0; i < contents.length; i++) {
                    ItemStack item = contents[i];
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(generators.moneyKey, PersistentDataType.INTEGER)) {
                        Integer itemValue = item.getItemMeta().getPersistentDataContainer().get(generators.moneyKey, PersistentDataType.INTEGER);
                        if (itemValue != null) {
                            Long baseAmt = item.getItemMeta().getPersistentDataContainer().get(generators.amountKey, PersistentDataType.LONG);
                            if (baseAmt == null) baseAmt = (long) item.getAmount();
                            totalAdded += (long) itemValue * baseAmt;
                            player.getInventory().setItem(i, null);
                        }
                    }
                }
                
                if (totalAdded > 0) {
                    economy.addBalance(player.getUniqueId(), totalAdded);
                    if (milestones != null) milestones.addMoneyGenerated(player.getUniqueId(), totalAdded);
                    player.sendMessage("§a$" + PlayerSessionListener.formatValue(totalAdded) + " deposited to your account.");
                    sessionListener.updateScoreboard(player);
                }
            }
        }
    }
}
