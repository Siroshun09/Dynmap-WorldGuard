package org.dynmap.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.BooleanFlag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class DynmapWorldGuardPlugin extends JavaPlugin {
    DynmapAPI api;
    BooleanFlag boost_flag;
    MarkerSet set;
    AreaStyle defstyle;
    Map<String, AreaStyle> cusstyle;
    Map<String, AreaStyle> cuswildstyle;
    Map<String, AreaStyle> ownerstyle;
    Map<String, AreaMarker> resareas = new HashMap<>();
    Set<String> visible;
    Set<String> hidden;

    @Override
    public void onLoad() {
        this.registerCustomFlags();
    }

    @Override
    public void onEnable() {
        /* Get dynmap */
        Plugin dynmap = getServer().getPluginManager().getPlugin("dynmap");
        if (dynmap == null) {
            getLogger().severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI) dynmap; /* Get API */

        /* Get WorldGuard */
        Plugin wg = getServer().getPluginManager().getPlugin("WorldGuard");
        if (wg == null) {
            getLogger().severe("Cannot find WorldGuard!");
            return;
        }

        activate();
        getLogger().info(getName() + " v" + getDescription().getVersion() + " has been successfully enabled.");
    }

    private void registerCustomFlags() {
        try {
            boost_flag = new BooleanFlag("dynmap-boost");
            if (WorldGuard.getInstance().getFlagRegistry().get("dynmap-boost") == null) {
                WorldGuard.getInstance().getFlagRegistry().register(boost_flag);
            }
        } catch (Exception x) {
            getLogger().info("Error registering flag - " + x.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        resareas.clear();
    }

    private void activate() {
        /* Now, get markers API */
        MarkerAPI markerapi = api.getMarkerAPI();
        if (markerapi == null) {
            getLogger().severe("Error loading dynmap marker API!");
            return;
        }

        saveDefaultConfig();
        reloadConfig();

        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.getMarkerSet("worldguard.markerset");
        if (set == null) {
            set = markerapi.createMarkerSet("worldguard.markerset", getString("layer.name", "WorldGuard"), null, false);
        } else {
            set.setMarkerSetLabel(getString("layer.name", "WorldGuard"));
        }

        if (set == null) {
            getLogger().severe("Error creating marker set");
            return;
        }

        int minzoom = getInt("layer.minzoom", 0);
        if (minzoom > 0) {
            set.setMinZoom(minzoom);
        }

        set.setLayerPriority(getInt("layer.layerprio", 10));
        set.setHideByDefault(getBoolean("layer.hidebydefault"));

        /* Get style information */
        defstyle = new AreaStyle(this);

        cusstyle = new HashMap<>();
        ownerstyle = new HashMap<>();
        cuswildstyle = new HashMap<>();

        ConfigurationSection section = getConfig().getConfigurationSection("custstyle");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (id.indexOf('|') >= 0)
                    cuswildstyle.put(id, new AreaStyle(this, "custstyle." + id, defstyle));
                else
                    cusstyle.put(id, new AreaStyle(this, "custstyle." + id, defstyle));
            }
        }

        section = getConfig().getConfigurationSection("ownerstyle");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ownerstyle.put(id.toLowerCase(), new AreaStyle(this, "ownerstyle." + id, defstyle));
            }
        }

        visible = Set.copyOf(getConfig().getStringList("visibleregions"));
        hidden = Set.copyOf(getConfig().getStringList("hiddenregions"));

        getServer().getScheduler().scheduleSyncDelayedTask(this, new UpdateTask(this), 40);
    }

    String getString(String key, String def) {
        String value = getConfig().getString(key);
        if (value == null && def == null) {
            throw new NullPointerException();
        } else {
            return value == null ? def : value;
        }
    }

    int getInt(String key, int def) {
        return getConfig().getInt(key, def);
    }

    boolean getBoolean(String key) {
        return getConfig().getBoolean(key, false);
    }

    double getDouble(String key, double def) {
        return getConfig().getDouble(key, def);
    }
}
