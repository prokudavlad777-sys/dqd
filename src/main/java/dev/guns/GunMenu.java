package dev.guns;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Меню вибору зброї, яке відкриває /gun без аргументів. */
public final class GunMenu implements InventoryHolder {

    public static final int SLOT_REVOLVER = 3;
    public static final int SLOT_SHOTGUN = 5;

    private final Inventory inventory;

    public GunMenu(GunItems items) {
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Вибери зброю"));

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        inventory.setItem(SLOT_REVOLVER, items.createMenuIcon(GunType.REVOLVER));
        inventory.setItem(SLOT_SHOTGUN, items.createMenuIcon(GunType.SHOTGUN));
    }

    public static GunType typeAt(int slot) {
        if (slot == SLOT_REVOLVER) return GunType.REVOLVER;
        if (slot == SLOT_SHOTGUN) return GunType.SHOTGUN;
        return null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
