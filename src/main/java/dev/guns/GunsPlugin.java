package dev.guns;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class GunsPlugin extends JavaPlugin {

    private GunItems items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new GunItems(this);

        getServer().getPluginManager().registerEvents(new GunListener(this, items), this);

        GunCommand command = new GunCommand(items);
        PluginCommand gun = Objects.requireNonNull(getCommand("gun"), "команда gun не знайдена в plugin.yml");
        gun.setExecutor(command);
        gun.setTabCompleter(command);

        getLogger().info("GunsPlugin увімкнено.");
    }

    public GunItems items() {
        return items;
    }
}
