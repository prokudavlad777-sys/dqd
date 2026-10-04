package dev.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class GunCommand implements CommandExecutor, TabCompleter {

    private final GunItems items;

    public GunCommand(GunItems items) {
        this.items = items;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Цю команду може використовувати тільки гравець.");
            return true;
        }

        if (args.length == 0) {
            player.openInventory(new GunMenu(items).getInventory());
            return true;
        }

        GunType type = GunType.parse(args[0]);
        if (type == null) {
            player.sendMessage(Component.text("Невідома зброя. Використання: /gun revolver | /gun shotgun", NamedTextColor.RED));
            return true;
        }

        items.giveKit(player, type);
        player.sendMessage(Component.text("Ти отримав: " + type.gunName + " і " + items.giveAmount() + " патронів.", NamedTextColor.GREEN));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String option : List.of("revolver", "shotgun")) {
                if (option.startsWith(prefix)) result.add(option);
            }
        }
        return result;
    }
}
