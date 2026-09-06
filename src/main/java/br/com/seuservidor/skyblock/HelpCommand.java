package br.com.seuservidor.skyblock;

import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

public final class HelpCommand implements CommandExecutor {
    
    public HelpCommand(SkyblockPlugin plugin) {
        plugin.getCommand("help").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command is for players only.");
            return true;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        
        if (meta != null) {
            meta.setTitle("§6Server Guide");
            meta.setAuthor("§cAdmin");
            
            String page1 = "§lCore Commands:§r\n\n" +
                           "§9/island create§0\nCreate your island\n\n" +
                           "§9/island home§0\nTeleport home\n\n" +
                           "§9/island info§0\nView island info\n\n" +
                           "§9/lobby§0\nReturn to lobby\n\n" +
                           "§9/shop§0\nOpen server shop";

            String page2 = "§lEconomy:§r\n\n" +
                           "§9/market§0\nOpen player market\n\n" +
                           "§9/market sell <price>§0\nSell held item stack\n\n" +
                           "§9/trash§0\nDelete unwanted items\n\n" +
                           "§9/milestones§0\nView progress and limits";

            String page3 = "§lAdmin/Test:§r\n\n" +
                           "§9/generator give <player> <type> [amount]§0\nGive generators\n\n" +
                           "§9/rank set <player> <rank>§0\nSet rank\n\n" +
                           "§9/adminreset <player>§0\nReset progress\n\n" +
                           "§9/opme§0\nTest-only OP command";
                           
            meta.addPage(page1);
            meta.addPage(page2);
            meta.addPage(page3);
            book.setItemMeta(meta);
        }

        player.getInventory().addItem(book);
        player.sendMessage("§aYou received the server guide book!");
        return true;
    }
}
