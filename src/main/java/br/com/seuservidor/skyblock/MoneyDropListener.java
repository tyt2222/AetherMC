package br.com.seuservidor.skyblock;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class MoneyDropListener implements Listener {
    private final GeneratorManager generators;

    public MoneyDropListener(GeneratorManager generators) {
        this.generators = generators;
    }

    private Integer getValue(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(generators.moneyKey, PersistentDataType.INTEGER);
    }

    private Integer getAmount(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(generators.amountKey, PersistentDataType.INTEGER);
    }

    @EventHandler
    public void onMerge(ItemMergeEvent event) {
        ItemStack item1 = event.getEntity().getItemStack();
        ItemStack item2 = event.getTarget().getItemStack();

        Integer val1 = getValue(item1);
        Integer val2 = getValue(item2);

        if (val1 != null && val2 != null && val1.equals(val2)) {
            event.setCancelled(true);
            
            int amt1 = getAmount(item1) != null ? getAmount(item1) : item1.getAmount();
            int amt2 = getAmount(item2) != null ? getAmount(item2) : item2.getAmount();
            int total = amt1 + amt2;

            ItemStack combined = generators.createMoney(val1, total);
            event.getTarget().setItemStack(combined);
            event.getTarget().setCustomName("§a" + val1 + " §8(x" + total + ")");
            event.getTarget().setCustomNameVisible(true);
            
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        Integer val = getValue(item);
        Integer amt = getAmount(item);

        if (val != null && amt != null) {
            event.setCancelled(true);
            event.getItem().remove();

            int remaining = amt;
            while (remaining > 0) {
                int chunk = Math.min(64, remaining);
                ItemStack stack = generators.createMoney(val, 1);
                stack.setAmount(chunk);
                player.getInventory().addItem(stack);
                remaining -= chunk;
            }
            
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 2.0f);
        }
    }
}
