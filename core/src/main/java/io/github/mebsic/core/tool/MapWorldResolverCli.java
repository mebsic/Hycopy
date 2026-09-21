package io.github.mebsic.core.tool;

import io.github.mebsic.core.manager.MongoManager;
import io.github.mebsic.core.store.MapConfigStore;

import java.util.Locale;

public final class MapWorldResolverCli {
    private MapWorldResolverCli() {
    }

    public static void main(String[] args) {
        String mongoUri = arg(args, 0);
        String mongoDatabase = arg(args, 1);
        String gameType = arg(args, 2);
        String serverKind = arg(args, 3);
        String excludedMapName = arg(args, 4);

        if (mongoUri.isEmpty() || mongoDatabase.isEmpty()) {
            return;
        }

        boolean hubServer = serverKind.toLowerCase(Locale.ROOT).contains("hub");
        String gameKey = MapConfigStore.normalizeGameKey(gameType);
        if (gameKey.isEmpty() && hubServer) {
            gameKey = MongoManager.MAP_CONFIG_DEFAULT_GAME_KEY;
        }
        if (gameKey.isEmpty()) {
            return;
        }

        MongoManager mongo = null;
        try {
            mongo = new MongoManager(mongoUri, mongoDatabase);
            MapConfigStore store = new MapConfigStore(mongo);

            String worldDirectory;
            if (hubServer) {
                store.ensureDefaults(gameKey);
                worldDirectory = store.resolveWorldDirectory(gameKey, true);
            } else {
                worldDirectory = store.resolveRandomGameWorldDirectory(gameKey, excludedMapName);
            }
            if (hubServer && worldDirectory.isEmpty() && !MongoManager.MAP_CONFIG_DEFAULT_GAME_KEY.equals(gameKey)) {
                store.ensureDefaults(MongoManager.MAP_CONFIG_DEFAULT_GAME_KEY);
                worldDirectory = store.resolveWorldDirectory(MongoManager.MAP_CONFIG_DEFAULT_GAME_KEY, true);
            }
            printIfNotBlank(worldDirectory);
        } catch (Exception ignored) {
            // Resolver intentionally emits no fallback when Mongo is unavailable.
        } finally {
            if (mongo != null) {
                mongo.close();
            }
        }
    }

    private static String arg(String[] args, int index) {
        if (args == null || index < 0 || index >= args.length || args[index] == null) {
            return "";
        }
        String value = args[index].trim();
        return value.isEmpty() ? "" : value;
    }

    private static void printIfNotBlank(String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        System.out.print(value.trim());
    }
}
