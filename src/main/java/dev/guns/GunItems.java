package dev.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Створення та розпізнавання зброї й патронів. */
public final class GunItems {

    private final GunsPlugin plugin;
    private final NamespacedKey keyGun;
    private final NamespacedKey keyLoaded;
    private final NamespacedKey keyAmmo;

    public GunItems(GunsPlugin plugin) {
        this.plugin = plugin;
        this.keyGun = new NamespacedKey(plugin, "gun");
        this.keyLoaded = new NamespacedKey(plugin, "loaded");
        this.keyAmmo = new NamespacedKey(plugin, "ammo");
    }

    // ---------- налаштування ----------

    public int magazine(GunType type) {
        return Math.max(1, plugin.getConfig().getInt(type.id + ".magazine", type == GunType.REVOLVER ? 6 : 2));
    }

    public int giveAmount() {
        return Math.max(1, Math.min(64, plugin.getConfig().getInt("give-amount", 64)));
    }

    // ---------- створення ----------

    private static Component text(String s, NamedTextColor color) {
        return Component.text(s, color).decoration(TextDecoration.ITALIC, false);
    }

    public ItemStack createGun(GunType type) {
        ItemStack item = new ItemStack(type.gunMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(type.gunName, NamedTextColor.GOLD));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(keyGun, PersistentDataType.STRING, type.id);
        meta.getPersistentDataContainer().set(keyLoaded, PersistentDataType.INTEGER, magazine(type));
        meta.lore(gunLore(type, magazine(type)));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createAmmo(GunType type, int amount) {
        ItemStack item = new ItemStack(type.ammoMaterial, amount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(type.ammoName, NamedTextColor.YELLOW));
        meta.getPersistentDataContainer().set(keyAmmo, PersistentDataType.STRING, type.id);
        meta.lore(List.of(text("Для: " + type.gunName, NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    /** Іконка для меню /gun (не є справжньою зброєю). */
    public ItemStack createMenuIcon(GunType type) {
        ItemStack item = new ItemStack(type.gunMaterial);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(type.gunName, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(text("Натисни, щоб отримати", NamedTextColor.GREEN));
        lore.add(text("зброю + " + giveAmount() + " патронів", NamedTextColor.GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> gunLore(GunType type, int loaded) {
        List<Component> lore = new ArrayList<>();
        lore.add(text("Заряджено: " + loaded + "/" + magazine(type), NamedTextColor.GRAY));
        lore.add(text("ПКМ — стріляти", NamedTextColor.DARK_GRAY));
        lore.add(text("F — перезарядити", NamedTextColor.DARK_GRAY));
        return lore;
    }

    public void giveKit(Player player, GunType type) {
        List<ItemStack> stacks = List.of(createGun(type), createAmmo(type, giveAmount()));
        for (ItemStack stack : stacks) {
            Map<Integer, ItemStack> left = player.getInventory().addItem(stack);
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ---------- розпізнавання ----------

    public GunType gunType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(keyGun, PersistentDataType.STRING);
        return GunType.byId(id);
    }

    public GunType ammoType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(keyAmmo, PersistentDataType.STRING);
        return GunType.byId(id);
    }

    public int getLoaded(ItemStack gun) {
        if (gun == null || !gun.hasItemMeta()) return 0;
        Integer v = gun.getItemMeta().getPersistentDataContainer().get(keyLoaded, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    /** Повертає копію зброї з оновленою кількістю набоїв у барабані. */
    public ItemStack withLoaded(ItemStack gun, GunType type, int loaded) {
        ItemStack copy = gun.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(keyLoaded, PersistentDataType.INTEGER, loaded);
        meta.lore(gunLore(type, loaded));
        copy.setItemMeta(meta);
        return copy;
    }

    // ---------- патрони в інвентарі ----------

    public int countAmmo(Player player, GunType type) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && ammoType(stack) == type) total += stack.getAmount();
        }
        return total;
    }

    /** Забирає до {@code amount} патронів з інвентарю, повертає скільки забрано. */
    public int takeAmmo(Player player, GunType type, int amount) {
        PlayerInventory inv = player.getInventory();
        int remaining = amount;
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || ammoType(stack) != type) continue;
            int take = Math.min(remaining, stack.getAmount());
            remaining -= take;
            if (take >= stack.getAmount()) {
                inv.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - take);
                inv.setItem(i, stack);
            }
        }
        return amount - remaining;
    }
}
