package io.github.mebsic.murdermystery.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sends full 128x128 map pixel payloads when Bukkit's wrapper only flushes cursors reliably.
 */
final class MurderMysteryMapPacketSender {
    private static final int MAP_PIXEL_SIZE = 128;

    private final Constructor<?> packetConstructor;
    private final Constructor<?> rawMapIconConstructor;
    private final Constructor<?> typedMapIconConstructor;
    private final Object[] mapIconTypes;
    private final Class<?> packetBaseClass;

    private MurderMysteryMapPacketSender(Constructor<?> packetConstructor,
                                        Constructor<?> rawMapIconConstructor,
                                        Constructor<?> typedMapIconConstructor,
                                        Object[] mapIconTypes,
                                        Class<?> packetBaseClass) {
        this.packetConstructor = packetConstructor;
        this.rawMapIconConstructor = rawMapIconConstructor;
        this.typedMapIconConstructor = typedMapIconConstructor;
        this.mapIconTypes = mapIconTypes;
        this.packetBaseClass = packetBaseClass;
    }

    static MurderMysteryMapPacketSender create() {
        String version = serverVersion();
        if (version.isEmpty()) {
            return null;
        }
        try {
            String nmsPackage = "net.minecraft.server." + version;
            Class<?> packetClass = Class.forName(nmsPackage + ".Packet");
            Class<?> mapPacketClass = Class.forName(nmsPackage + ".PacketPlayOutMap");
            Class<?> mapIconClass = Class.forName(nmsPackage + ".MapIcon");
            Constructor<?> packetConstructor = mapPacketClass.getConstructor(
                    int.class,
                    byte.class,
                    Collection.class,
                    byte[].class,
                    int.class,
                    int.class,
                    int.class,
                    int.class
            );
            Constructor<?> rawIconConstructor = rawMapIconConstructor(mapIconClass);
            Constructor<?> typedIconConstructor = typedMapIconConstructor(mapIconClass);
            Object[] mapIconTypes = typedIconConstructor == null
                    ? null
                    : typedIconConstructor.getParameterTypes()[0].getEnumConstants();
            if (rawIconConstructor == null && (typedIconConstructor == null || mapIconTypes == null)) {
                return null;
            }
            return new MurderMysteryMapPacketSender(
                    packetConstructor,
                    rawIconConstructor,
                    typedIconConstructor,
                    mapIconTypes,
                    packetClass
            );
        } catch (Throwable ignored) {
            return null;
        }
    }

    boolean send(Player player, int mapId, byte scale, byte[] pixels, MapCursorCollection cursors) {
        if (player == null || pixels == null || pixels.length < MAP_PIXEL_SIZE * MAP_PIXEL_SIZE) {
            return false;
        }
        try {
            List<Object> icons = createMapIcons(cursors);
            Object packet = packetConstructor.newInstance(
                    mapId,
                    scale,
                    icons,
                    pixels,
                    0,
                    0,
                    MAP_PIXEL_SIZE,
                    MAP_PIXEL_SIZE
            );
            return sendPacket(player, packet);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private List<Object> createMapIcons(MapCursorCollection cursors) throws Exception {
        List<Object> icons = new ArrayList<>();
        if (cursors == null) {
            return icons;
        }
        for (int i = 0; i < cursors.size(); i++) {
            MapCursor cursor = cursors.getCursor(i);
            if (cursor == null || !cursor.isVisible()) {
                continue;
            }
            icons.add(createMapIcon(cursor));
        }
        return icons;
    }

    private Object createMapIcon(MapCursor cursor) throws Exception {
        byte type = cursorTypeId(cursor.getType());
        if (rawMapIconConstructor != null) {
            return rawMapIconConstructor.newInstance(type, cursor.getX(), cursor.getY(), cursor.getDirection());
        }
        int index = type & 15;
        Object iconType = index < mapIconTypes.length ? mapIconTypes[index] : mapIconTypes[0];
        return typedMapIconConstructor.newInstance(iconType, cursor.getX(), cursor.getY(), cursor.getDirection());
    }

    private boolean sendPacket(Player player, Object packet) {
        if (player == null || packet == null) {
            return false;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = handle.getClass().getField("playerConnection").get(handle);
            Method sendPacket = connection.getClass().getMethod("sendPacket", packetBaseClass);
            sendPacket.invoke(connection, packet);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Constructor<?> rawMapIconConstructor(Class<?> mapIconClass) {
        try {
            return mapIconClass.getConstructor(byte.class, byte.class, byte.class, byte.class);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Constructor<?> typedMapIconConstructor(Class<?> mapIconClass) {
        for (Class<?> nested : mapIconClass.getDeclaredClasses()) {
            if (!nested.isEnum()) {
                continue;
            }
            try {
                return mapIconClass.getConstructor(nested, byte.class, byte.class, byte.class);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static byte cursorTypeId(MapCursor.Type type) {
        if (type == MapCursor.Type.GREEN_POINTER) {
            return 1;
        }
        if (type == MapCursor.Type.RED_POINTER) {
            return 2;
        }
        if (type == MapCursor.Type.BLUE_POINTER) {
            return 3;
        }
        if (type == MapCursor.Type.WHITE_CROSS) {
            return 4;
        }
        return 0;
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
