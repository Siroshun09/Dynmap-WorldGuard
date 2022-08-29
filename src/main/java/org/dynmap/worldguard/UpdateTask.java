package org.dynmap.worldguard;

import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionType;
import com.sk89q.worldguard.util.profile.cache.ProfileCache;
import org.bukkit.Bukkit;
import org.dynmap.markers.AreaMarker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpdateTask implements Runnable {

    final Map<String, AreaMarker> newmap = new HashMap<>(); /* Build new map */
    private final DynmapWorldGuardPlugin plugin;
    List<World> worldsToDo;
    List<ProtectedRegion> regionsToDo;
    World curworld;


    UpdateTask(DynmapWorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // If worlds list isn't primed, prime it
        if (worldsToDo == null) {
            worldsToDo = new ArrayList<>();
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                worldsToDo.add(WorldGuard.getInstance().getPlatform().getMatcher().getWorldByName(world.getName()));
            }
        }

        while (regionsToDo == null) {  // No pending regions for world
            if (worldsToDo.isEmpty()) { // No more worlds?
                /* Now, review old map - anything left is gone */
                for (AreaMarker oldm : plugin.resareas.values()) {
                    oldm.deleteMarker();
                }
                /* And replace with new map */
                plugin.resareas = newmap;
                // Set up for next update (new job)
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new UpdateTask(plugin), getUpdatesPeriod());
                return;
            } else {
                curworld = worldsToDo.remove(0);
                RegionManager rm = WorldGuard.getInstance().getPlatform().getRegionContainer().get(curworld); /* Get region manager for world */
                if (rm != null) {
                    Map<String, ProtectedRegion> regions = rm.getRegions();  /* Get all the regions */
                    if (!regions.isEmpty()) {
                        regionsToDo = new ArrayList<>(regions.values());
                    }
                }
            }
        }
        /* Now, process up to limit regions */
        int updatesPerTick = plugin.getInt("updates-per-tick", 20);
        for (int i = 0; i < updatesPerTick; i++) {
            if (regionsToDo.isEmpty()) {
                regionsToDo = null;
                break;
            }
            ProtectedRegion pr = regionsToDo.remove(regionsToDo.size() - 1);
            int depth = 1;
            ProtectedRegion p = pr;
            while (p.getParent() != null) {
                depth++;
                p = p.getParent();
            }
            if (depth > plugin.getInt("maxdepth", 16)) {
                continue;
            }
            handleRegion(curworld, pr, newmap);
        }
        // Tick next step in the job
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, this, 1);
    }

    private String formatInfoWindow(ProtectedRegion rg, AreaMarker m) {
        ProfileCache pc = WorldGuard.getInstance().getProfileCache();
        String v = "<div class=\"regioninfo\">" + getInfoWindow() + "</div>";
        v = v.replace("%regionname%", m.getLabel())
                .replace("%playerowners%", rg.getOwners().toPlayersString(pc))
                .replace("%groupowners%", rg.getOwners().toGroupsString())
                .replace("%playermembers%", rg.getMembers().toPlayersString(pc))
                .replace("%groupmembers%", rg.getMembers().toGroupsString())
                .replace("%parent%", rg.getParent() == null ? "" : rg.getParent().getId())
                .replace("%priority%", String.valueOf(rg.getPriority()));

        Map<Flag<?>, Object> map = rg.getFlags();
        StringBuilder flgs = new StringBuilder();
        for (Flag<?> f : map.keySet()) {
            flgs.append(f.getName()).append(": ").append(map.get(f).toString()).append("<br/>");
        }
        v = v.replace("%flags%", flgs.toString());
        return v;
    }

    private boolean isVisible(String id, String worldname) {
        if ((plugin.visible != null) && (plugin.visible.size() > 0)) {
            if ((!plugin.visible.contains(id))
                    && (!plugin.visible.contains("world:" + worldname)) && (!plugin.visible.contains(worldname + "/" + id))) {
                return false;
            }
        }
        if ((plugin.hidden != null) && (plugin.hidden.size() > 0)) {
            return !plugin.hidden.contains(id)
                    && !plugin.hidden.contains("world:" + worldname)
                    && !plugin.hidden.contains(worldname + "/" + id);
        }
        return true;
    }

    /* Handle specific region */
    void handleRegion(World world, ProtectedRegion region, Map<String, AreaMarker> newmap) {
        String name = region.getId();
        /* Make first letter uppercase */
        name = name.substring(0, 1).toUpperCase() + name.substring(1);
        double[] x;
        double[] z;

        /* Handle areas */
        if (isVisible(region.getId(), world.getName())) {
            String id = region.getId();
            RegionType tn = region.getType();
            BlockVector3 l0 = region.getMinimumPoint();
            BlockVector3 l1 = region.getMaximumPoint();

            if (tn == RegionType.CUBOID) { /* Cubiod region? */
                /* Make outline */
                x = new double[4];
                z = new double[4];
                x[0] = l0.getX();
                z[0] = l0.getZ();
                x[1] = l0.getX();
                z[1] = l1.getZ() + 1.0;
                x[2] = l1.getX() + 1.0;
                z[2] = l1.getZ() + 1.0;
                x[3] = l1.getX() + 1.0;
                z[3] = l0.getZ();
            } else if (tn == RegionType.POLYGON) {
                ProtectedPolygonalRegion ppr = (ProtectedPolygonalRegion) region;
                List<BlockVector2> points = ppr.getPoints();
                x = new double[points.size()];
                z = new double[points.size()];
                for (int i = 0; i < points.size(); i++) {
                    BlockVector2 pt = points.get(i);
                    x[i] = pt.getX();
                    z[i] = pt.getZ();
                }
            } else {  /* Unsupported type */
                return;
            }
            String markerid = world.getName() + "_" + id;
            AreaMarker m = plugin.resareas.remove(markerid); /* Existing area? */
            if (m == null) {
                m = plugin.set.createAreaMarker(markerid, name, false, world.getName(), x, z, false);
                if (m == null)
                    return;
            } else {
                m.setCornerLocations(x, z); /* Replace corner locations */
                m.setLabel(name);   /* Update label */
            }
            if (plugin.getBoolean("use3dregions")) { /* If 3D? */
                m.setRangeY(l1.getY() + 1.0, l0.getY());
            }

            /* Set line and fill properties */
            addStyle(m, region);

            /* Build popup */
            String desc = formatInfoWindow(region, m);

            m.setDescription(desc); /* Set popup */

            /* Add to map */
            newmap.put(markerid, m);
        }
    }

    private void addStyle(AreaMarker m, ProtectedRegion region) {
        AreaStyle as = plugin.defstyle;

        boolean unowned = (region.getOwners().getPlayers().size() == 0) &&
                (region.getOwners().getUniqueIds().size() == 0) &&
                (region.getOwners().getGroups().size() == 0);
        int sc = 0xFF0000;
        int fc = 0xFF0000;
        try {
            if (unowned)
                sc = Integer.parseInt(as.unownedstrokecolor.substring(1), 16);
            else
                sc = Integer.parseInt(as.strokecolor.substring(1), 16);
            fc = Integer.parseInt(as.fillcolor.substring(1), 16);
        } catch (NumberFormatException ignored) {
        }
        m.setLineStyle(as.strokeweight, as.strokeopacity, sc);
        m.setFillStyle(as.fillopacity, fc);
        if (as.label != null) {
            m.setLabel(as.label);
        }
        if (plugin.boost_flag != null) {
            Boolean b = region.getFlag(plugin.boost_flag);
            m.setBoostFlag((b != null) && b);
        }
    }

    private String getInfoWindow() {
        return plugin.getString("infowindow",
                "<div class=\"infowindow\"><span style=\"font-size:120%;\">%regionname%</span><br /> Owner <span style=\"" +
                        "font-weight:bold;\">%playerowners%</span><br />Flags<br /><span style=\"font-weight:bold;\">%flags%</span></div>");
    }

    private long getUpdatesPeriod() {
        int per = plugin.getInt("update.period", 300);
        if (per < 15) per = 15;
        return per * 20L;
    }
}
