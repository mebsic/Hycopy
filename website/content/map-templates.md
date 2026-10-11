Saving exports a reusable world template and updates its map metadata. Hub and game servers then resolve a map and apply its template during startup.

## Saving a Map

When you click **Save Map**, the build plugin:

1. Verifies no other players are in that world.
2. Teleports you to a transit world.
3. Saves and unloads the source world.
4. Copies source world folder to configured map roots:

   ```text
   /production/maps/<gameKey>/<sourceWorldName>
   /development/maps/<gameKey>/<sourceWorldName> (if MAP_ROOT_DEVELOPMENT is set)
   ```

5. Updates Mongo map config for that `gameKey` if the primary export succeeds:
   - creates/updates `worldDirectory`
   - normalizes `name` from `worldDirectory`
   - applies rotation/active defaults
6. Reloads the world and teleports you back.

## Rotation and Active Map Behavior

On build edits/exports:

- Map is ensured in `gameTypes.<gameKey>.rotation`.
- If `activeMap` is empty, it is set to the edited `worldDirectory`.
- If `activeMap` is an alias (`hub`, `default`, `world`, `world_nether`, `world_the_end`), it is migrated to the concrete `worldDirectory`.
- Setting **Hub Spawn** sets `activeMap` to that map.

## Runtime (Paper Entrypoint)

Runtime uses:

- `DATA_DIR` (default `/data`)
- `GAME_TYPE` (default `murdermystery`)
- `SERVER_KIND` (default `game`)
- `MAP_NAME` (optional)
- `WORLD_NAME` (defaults to `world`)
- `MAP_APPLY_MODE` (`copy`, `link`, or `symlink`)
- `MAP_ROOT` (default `/maps`)
- `FORCE_MAP_COPY` (default `false`)

When `MAP_NAME` is empty on non-build servers, bootstrap resolves map from Mongo first, then data-world marker/level-name logic, then local fallback directory selection.

If `WORLD_NAME` is unset, or is `world` on a non-build server, runtime can automatically set the world/level name to the resolved map name.

Template source base:

```text
/maps/${GAME_TYPE}/${MAP_NAME}
```

Apply modes:

- `copy`:
  - copies resolved template into `/data/${WORLD_NAME}`
  - writes `.hycopy-map-source` marker
- `link` / `symlink`:
  - creates `/data/${MAP_NAME}` symlink to template
  - sets `level-name=${MAP_NAME}`
