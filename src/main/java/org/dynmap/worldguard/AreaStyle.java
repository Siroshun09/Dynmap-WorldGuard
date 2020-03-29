package org.dynmap.worldguard;

public class AreaStyle {
    final String strokecolor;
    final String unownedstrokecolor;
    final double strokeopacity;
    final int strokeweight;
    final String fillcolor;
    final double fillopacity;
    String label;

    AreaStyle(DynmapWorldGuardPlugin plugin, String path, AreaStyle def) {
        strokecolor = plugin.getString(path + ".strokeColor", def.strokecolor);
        unownedstrokecolor = plugin.getString(path + ".unownedStrokeColor", def.unownedstrokecolor);
        strokeopacity = plugin.getDouble(path + ".strokeOpacity", def.strokeopacity);
        strokeweight = plugin.getInt(path + ".strokeWeight", def.strokeweight);
        fillcolor = plugin.getString(path + ".fillColor", def.fillcolor);
        fillopacity = plugin.getDouble(path + ".fillOpacity", def.fillopacity);
        label = plugin.getString(path + ".label", "unknown");
    }

    AreaStyle(DynmapWorldGuardPlugin plugin) {
        strokecolor = plugin.getString("regionstyle" + ".strokeColor", "#FF0000");
        unownedstrokecolor = plugin.getString("regionstyle" + ".unownedStrokeColor", "#00FF00");
        strokeopacity = plugin.getDouble("regionstyle" + ".strokeOpacity", 0.8);
        strokeweight = plugin.getInt("regionstyle" + ".strokeWeight", 3);
        fillcolor = plugin.getString("regionstyle" + ".fillColor", "#FF0000");
        fillopacity = plugin.getDouble("regionstyle" + ".fillOpacity", 0.35);
    }
}
