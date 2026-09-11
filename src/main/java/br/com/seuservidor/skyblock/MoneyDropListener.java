package br.com.seuservidor.skyblock;

import org.bukkit.Bukkit;
import org.bukkit.World;
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
        Bukkit.getScheduler().runTaskTimer(generators.getPlugin(), this::mergeNearbyDrops, 10L, 10L);
    }

    private void mergeNearbyDrops() {
        for (World world : Bukkit.getWorlds()) {
            for (Item source : world.getEntitiesByClass(Item.class)) {
                if (!source.isValid()) continue;
                Integer value = getValue(source.getItemStack());
                if (value == null) continue;
                for (org.bukkit.entity.Entity nearbyEntity : world.getNearbyEntities(source.getLocation(), 1.75, 1.75, 1.75,
                    entity -> entity instanceof Item && entity != source)) {
                    if (!(nearbyEntity instanceof Item target) || !target.isValid()) continue;
                    Integer targetValue = getValue(target.getItemStack());
                    if (!value.equals(targetValue)) continue;
                    long total = amount(source.getItemStack()) + amount(target.getItemStack());
                    target.setItemStack(generators.createMoney(value, total));
                    target.setCustomName("§2$§a" + value + " §8(x" + total + ")");
                    target.setCustomNameVisible(true);
                    source.remove();
                    break;
                }
            }
        }
    }

    private long amount(ItemStack stack) {
        Long stored = getAmount(stack);
        return stored != null ? stored : stack.getAmount();
    }

    private Integer getValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(generators.moneyKey, PersistentDataType.INTEGER);
    }

    private Long getAmount(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(generators.amountKey, PersistentDataType.LONG);
    }

    @EventHandler
    public void onMerge(ItemMergeEvent event) {
        ItemStack item1 = event.getEntity().getItemStack();
        ItemStack item2 = event.getTarget().getItemStack();

        Integer val1 = getValue(item1);
        Integer val2 = getValue(item2);

        if (val1 != null && val2 != null && val1.equals(val2)) {
            event.setCancelled(true);
            long amt1 = getAmount(item1) != null ? getAmount(item1) : item1.getAmount();
            long amt2 = getAmount(item2) != null ? getAmount(item2) : item2.getAmount();
            long total = amt1 + amt2;

            ItemStack combined = generators.createMoney(val1, total);
            event.getTarget().setItemStack(combined);
            event.getTarget().setCustomName("§2$§a" + val1 + " §8(x" + total + ")");
            event.getTarget().setCustomNameVisible(true);
            event.getEntity().remove();
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        ItemStack item = event.getItem().getItemStack();
        Integer val = getValue(item);
        Long storedAmount = getAmount(item);
        long amt = storedAmount != null ? storedAmount : item.getAmount();

        if (val != null) {
            event.setCancelled(true);
            event.getItem().remove();
            givePhysicalStacks(player, val, amt);
            
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 2.0f);
        }
    }

    private void givePhysicalStacks(Player player, int value, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            int chunk = (int) Math.min(64L, remaining);
            ItemStack stack = generators.createMoney(value, 1);
            ItemMeta meta = stack.getItemMeta();
            meta.getPersistentDataContainer().remove(generators.amountKey);
            stack.setItemMeta(meta);
            stack.setAmount(chunk);
            java.util.Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            remaining -= chunk;
        }
    }
}
