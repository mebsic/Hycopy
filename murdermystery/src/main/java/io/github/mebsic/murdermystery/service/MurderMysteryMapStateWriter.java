package io.github.mebsic.murdermystery.service;

import org.bukkit.Bukkit;
import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

final class MurderMysteryMapStateWriter {
    private static final int MAP_PIXEL_SIZE = 128;
    private static final int MAP_PIXELS = MAP_PIXEL_SIZE * MAP_PIXEL_SIZE;

    private final Field worldMapField;
    private final Field colorsField;
    private final Field decorationsField;
    private final Method flagDirtyMethod;

    private MurderMysteryMapStateWriter(Field worldMapField,
                                       Field colorsField,
                                       Field decorationsField,
                                       Method flagDirtyMethod) {
        this.worldMapField = worldMapField;
        this.colorsField = colorsField;
        this.decorationsField = decorationsField;
        this.flagDirtyMethod = flagDirtyMethod;
    }

    static MurderMysteryMapStateWriter create() {
        String version = serverVersion();
        if (version.isEmpty()) {
            return null;
        }
        try {
            Class<?> craftMapViewClass = Class.forName("org.bukkit.craftbukkit." + version + ".map.CraftMapView");
            Class<?> worldMapClass = Class.forName("net.minecraft.server." + version + ".WorldMap");
            Field worldMapField = craftMapViewClass.getDeclaredField("worldMap");
            Field colorsField = worldMapClass.getField("colors");
            Field decorationsField = worldMapClass.getField("decorations");
            Method flagDirtyMethod = worldMapClass.getMethod("flagDirty", int.class, int.class);
            worldMapField.setAccessible(true);
            return new MurderMysteryMapStateWriter(worldMapField, colorsField, decorationsField, flagDirtyMethod);
        } catch (Throwable ignored) {
            return null;
        }
    }

    boolean write(MapView mapView, byte[] pixels) {
        if (mapView == null || pixels == null || pixels.length < MAP_PIXELS) {
            return false;
        }
        try {
            Object worldMap = worldMapField.get(mapView);
            byte[] colors = colors(worldMap);
            System.arraycopy(pixels, 0, colors, 0, MAP_PIXELS);
            clearDecorations(worldMap);
            flagFullMapDirty(worldMap);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean clearDecorations(MapView mapView) {
        if (mapView == null) {
            return false;
        }
        try {
            clearDecorations(worldMapField.get(mapView));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private byte[] colors(Object worldMap) throws IllegalAccessException {
        Object rawColors = colorsField.get(worldMap);
        if (rawColors instanceof byte[] && ((byte[]) rawColors).length >= MAP_PIXELS) {
            return (byte[]) rawColors;
        }
        byte[] colors = new byte[MAP_PIXELS];
        colorsField.set(worldMap, colors);
        return colors;
    }

    @SuppressWarnings("unchecked")
    private void clearDecorations(Object worldMap) throws IllegalAccessException {
        Object decorations = decorationsField.get(worldMap);
        if (decorations instanceof Map) {
            ((Map<Object, Object>) decorations).clear();
        }
    }

    private void flagFullMapDirty(Object worldMap) throws Exception {
        flagDirtyMethod.invoke(worldMap, 0, 0);
        flagDirtyMethod.invoke(worldMap, MAP_PIXEL_SIZE - 1, MAP_PIXEL_SIZE - 1);
    }

    private static String serverVersion() {
        if (Bukkit.getServer() == null) {
            return "";
        }
        Package serverPackage = Bukkit.getServer().getClass().getPackage();
        if (serverPackage == null) {
            return "";
        }
        String packageName = serverPackage.getName();
        int separator = packageName.lastIndexOf('.');
        if (separator < 0 || separator >= packageName.length() - 1) {
            return "";
        }
        return packageName.substring(separator + 1);
    }
}
