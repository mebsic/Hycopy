Use the build server edit menu to configure Murder Mystery maps. The available actions depend on whether you are editing a Murder Mystery game map or a hub.

Start with [Creation](/murdermystery/creating-maps) if you have not entered edit mode yet. When you finish, [save and export your world](/map-templates#saving-a-map).

## Edit Menu Actions

### Game Type

- `Player Spawn` (left-click): appends to `maps[].spawns[]`
- `Player Spawn` (right-click): sets `maps[].pregameSpawn` (single waiting-spawn value)
- `Drop Item`: appends to `maps[].dropItem[]`
  - For Murder Mystery, drop items are stored as `GOLD_INGOT`.
- `Locations`: list/teleport/delete saved entries
- `Save Map`: exports world template to map roots

### Hub Type

- `Hub Spawn`: toggles `maps[].hubSpawn`
  - When set, this map is promoted to `activeMap`.
- `Hub NPC Menu`: manages Click-to-Play and Profile NPC entries
- `Parkour`:
  - Click flow creates start/end route (with checkpoints via right-click)
  - If already configured, it resets parkour route data
- `Leaderboards`:
  - Click adds/resets selected metric leaderboard hologram entry
  - Right-click cycles leaderboard metric
- `Information`: adds/resets hub image display config
- `Locations`: list/teleport/delete saved entries
- `Save Map`: exports world template to map roots
