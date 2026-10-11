This guide covers Murder Mystery hub and game maps. Each map has a world template on disk and metadata in MongoDB. Start in a build world, select `MURDER_MYSTERY_HUB` or `MURDER_MYSTERY_GAME`, and configure it with the edit menu.

After entering edit mode, continue to [Editor](/murdermystery/edit-menu) and [Templates](/map-templates).

## Overview

Map metadata is stored in MongoDB:

```text
collection: maps
document _id (Murder Mystery): murdermystery
```

The map config is scoped under:

```json
{
  "gameTypes": {
    "murdermystery": {
      "activeMap": "...",
      "rotation": ["..."],
      "maps": [ ... ],
      "serverTypes": { ... },
      "rewards": { ... }
    }
  }
}
```

World templates on disk:

```text
docker/production/maps/<gameKey>/<worldDirectory>/
```

Build exports can also write to:

```text
docker/development/maps/<gameKey>/<worldDirectory>/
```

For Murder Mystery:

```text
docker/production/maps/murdermystery/<worldDirectory>/
docker/development/maps/murdermystery/<worldDirectory>/
```

## Naming Rules

- If a map name contains `hub` (case-insensitive), it is treated as a hub map.
- Otherwise, it is treated as a game map.
- `worldDirectory` is the canonical folder/map key.
- `name` is display text derived from `worldDirectory`.
  - Example: `archives_top_floor` -> `Archives Top Floor`

## Build Server Workflow

1. Join the build server (staff only).
2. Teleport into the target world.
3. Run:

   ```bash
   /edit <gameType>
   ```

Use explicit game types:

- `MURDER_MYSTERY_HUB`
- `MURDER_MYSTERY_GAME`

Notes:

- `/edit` is blocked in world named `world`.
- Hub/game edit mode must match world naming rule.
  - `hub` in world name => use hub mode
  - no `hub` in world name => use game mode
