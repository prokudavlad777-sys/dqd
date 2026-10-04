package dev.guns;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class GunListener implements Listener {

    private final GunsPlugin plugin;
    private final GunItems items;
    private final Set<UUID> reloading = new HashSet<>();
    private final Map<UUID, Long> lastShot = new HashMap<>();

    public GunListener(GunsPlugin plugin, GunItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    // ================= стрільба =================

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        GunType type = items.gunType(item);
        if (type == null) return;

        event.setCancelled(true);
        shoot(player, item, type);
    }

    /** Щоб можна було стріляти, коли дивишся прямо на моба. */
    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        GunType type = items.gunType(item);
        if (type == null) return;

        event.setCancelled(true);
        shoot(player, item, type);
    }

    private void shoot(Player player, ItemStack gun, GunType type) {
        UUID id = player.getUniqueId();
        FileConfiguration cfg = plugin.getConfig();

        if (reloading.contains(id)) {
            bar(player, "Перезарядка…", NamedTextColor.RED);
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = cfg.getLong(type.id + ".cooldown-ticks", 12) * 50L;
        Long last = lastShot.get(id);
        if (last != null && now - last < cooldown) return;

        int perShot = 1;
        if (type == GunType.SHOTGUN) {
            perShot = Math.max(1, Math.min(cfg.getInt("shotgun.shells-per-shot", 2), items.magazine(type)));
        }

        int loaded = items.getLoaded(gun);
        World world = player.getWorld();
        if (loaded < perShot) {
            lastShot.put(id, now);
            world.playSound(player.getLocation(), "block.dispenser.fail", SoundCategory.PLAYERS, 0.8f, 1.6f);
            bar(player, "Порожньо! Перезарядка — F", NamedTextColor.RED);
            return;
        }

        lastShot.put(id, now);
        player.getInventory().setItemInMainHand(items.withLoaded(gun, type, loaded - perShot));

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        double range = type == GunType.REVOLVER
                ? cfg.getDouble("revolver.range", 60.0)
                : cfg.getDouble("shotgun.max-range", 25.0);

        // Шкода накопичується по цілях, щоб два патрони обріза вдарили одним ударом.
        Map<LivingEntity, Double> damage = new LinkedHashMap<>();
        for (int i = 0; i < perShot; i++) {
            Vector d = type == GunType.SHOTGUN
                    ? spread(dir, cfg.getDouble("shotgun.spread-degrees", 2.0))
                    : dir.clone();

            RayTraceResult hit = world.rayTrace(eye, d, range, FluidCollisionMode.NEVER, true, 0.2,
                    entity -> isTarget(entity, player));

            double length = range;
            if (hit != null) {
                length = hit.getHitPosition().distance(eye.toVector());
                if (hit.getHitEntity() instanceof LivingEntity target) {
                    damage.merge(target, baseDamage(type, cfg, length), Double::sum);
                }
            }
            trail(world, eye, d, Math.min(length, 40.0));
        }

        for (Map.Entry<LivingEntity, Double> entry : damage.entrySet()) {
            entry.getKey().damage(entry.getValue(), player);
        }

        effects(world, eye, dir, type);
        if (type == GunType.SHOTGUN) {
            player.setVelocity(player.getVelocity().add(dir.clone().multiply(-0.3)));
        }

        int left = loaded - perShot;
        bar(player, "Набоїв: " + left + "/" + items.magazine(type),
                left == 0 ? NamedTextColor.RED : NamedTextColor.GRAY);
    }

    private static boolean isTarget(Entity entity, Player shooter) {
        if (!(entity instanceof LivingEntity) || entity.equals(shooter) || entity instanceof ArmorStand) {
            return false;
        }
        return !(entity instanceof Player p && p.getGameMode() == GameMode.SPECTATOR);
    }

    private static double baseDamage(GunType type, FileConfiguration cfg, double distance) {
        if (type == GunType.REVOLVER) {
            return cfg.getDouble("revolver.damage", 10.0);
        }
        double base = cfg.getDouble("shotgun.damage-per-shell", 270.0);
        double full = cfg.getDouble("shotgun.full-damage-range", 5.0);
        double max = cfg.getDouble("shotgun.max-range", 25.0);
        double min = cfg.getDouble("shotgun.min-damage-multiplier", 0.2);

        double multiplier;
        if (distance <= full) {
            multiplier = 1.0;
        } else if (max <= full) {
            multiplier = min;
        } else {
            double t = Math.min(1.0, (distance - full) / (max - full));
            multiplier = 1.0 + (min - 1.0) * t;
        }
        return base * multiplier;
    }

    private static Vector spread(Vector dir, double degrees) {
        double rad = Math.toRadians(degrees);
        Vector d = dir.clone().normalize();
        Vector helper = Math.abs(d.getY()) > 0.99 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector right = d.clone().crossProduct(helper).normalize();
        Vector up = right.clone().crossProduct(d).normalize();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double a = rnd.nextDouble(-rad, rad);
        double b = rnd.nextDouble(-rad, rad);
        return d.add(right.multiply(Math.tan(a))).add(up.multiply(Math.tan(b))).normalize();
    }

    private static void trail(World world, Location eye, Vector dir, double length) {
        for (double t = 1.5; t < length; t += 1.0) {
            Location point = eye.clone().add(dir.clone().multiply(t));
            world.spawnParticle(Particle.CRIT, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void effects(World world, Location eye, Vector dir, GunType type) {
        Location muzzle = eye.clone().add(dir.clone().multiply(0.9)).add(0, -0.2, 0);
        world.spawnParticle(Particle.FLAME, muzzle, type == GunType.SHOTGUN ? 10 : 4, 0.08, 0.08, 0.08, 0.02);
        if (type == GunType.SHOTGUN) {
            world.playSound(eye, "entity.generic.explode", SoundCategory.PLAYERS, 1.2f, 1.3f);
            world.playSound(eye, "entity.firework_rocket.blast", SoundCategory.PLAYERS, 1.5f, 0.6f);
        } else {
            world.playSound(eye, "entity.generic.explode", SoundCategory.PLAYERS, 0.6f, 1.9f);
            world.playSound(eye, "entity.firework_rocket.blast", SoundCategory.PLAYERS, 1.0f, 1.0f);
        }
    }

    // ================= перезарядка =================

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        GunType type = items.gunType(player.getInventory().getItemInMainHand());
        if (type == null) return;

        event.setCancelled(true);
        startReload(player, type);
    }

    private void startReload(Player player, GunType type) {
        UUID id = player.getUniqueId();
        if (reloading.contains(id)) return;

        ItemStack gun = player.getInventory().getItemInMainHand();
        int magazine = items.magazine(type);
        if (items.getLoaded(gun) >= magazine) {
            bar(player, "Вже заряджено", NamedTextColor.GRAY);
            return;
        }
        if (items.countAmmo(player, type) <= 0) {
            bar(player, "Немає патронів!", NamedTextColor.RED);
            player.getWorld().playSound(player.getLocation(), "block.dispenser.fail", SoundCategory.PLAYERS, 0.8f, 1.6f);
            return;
        }

        final int slot = player.getInventory().getHeldItemSlot();
        final int total = Math.max(2, plugin.getConfig().getInt(type.id + ".reload-ticks", 40));
        reloading.add(id);
        player.getWorld().playSound(player.getLocation(), "item.crossbow.loading_start", SoundCategory.PLAYERS, 1.0f, 1.0f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!player.isOnline()
                        || player.getInventory().getHeldItemSlot() != slot
                        || items.gunType(held) != type) {
                    reloading.remove(id);
                    if (player.isOnline()) bar(player, "Перезарядку скасовано", NamedTextColor.RED);
                    cancel();
                    return;
                }

                elapsed += 2;
                if (elapsed % 10 == 0) {
                    player.getWorld().playSound(player.getLocation(), "item.crossbow.loading_middle", SoundCategory.PLAYERS, 0.8f, 1.2f);
                }

                if (elapsed >= total) {
                    int loaded = items.getLoaded(held);
                    int taken = items.takeAmmo(player, type, magazine - loaded);
                    player.getInventory().setItemInMainHand(items.withLoaded(held, type, loaded + taken));
                    player.getWorld().playSound(player.getLocation(), "block.iron_trapdoor.close", SoundCategory.PLAYERS, 1.0f, 1.5f);
                    bar(player, "Заряджено: " + (loaded + taken) + "/" + magazine, NamedTextColor.GREEN);
                    reloading.remove(id);
                    cancel();
                    return;
                }

                int filled = Math.min(10, elapsed * 10 / total);
                String progress = "█".repeat(filled) + "░".repeat(10 - filled);
                bar(player, "Перезарядка " + progress, NamedTextColor.YELLOW);
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    // ================= меню /gun =================

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GunMenu)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        GunType type = GunMenu.typeAt(event.getSlot());
        if (type == null) return;

        items.giveKit(player, type);
        player.closeInventory();
        player.sendMessage(Component.text("Ти отримав: " + type.gunName + " і " + items.giveAmount() + " патронів.", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof GunMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        reloading.remove(id);
        lastShot.remove(id);
    }

    private static void bar(Player player, String text, NamedTextColor color) {
        player.sendActionBar(Component.text(text, color));
    }
}
