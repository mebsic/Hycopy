The Docker stack manages the Minecraft network. Its internal control-panel service coordinates plugin updates and container restarts.

## Start Order

- `control-panel` waits for healthy `mongo` and `redis`.
- Before `control-panel` API starts, its entrypoint copies required jars from `docker/development/plugins/` to `docker/production/plugins/` and verifies all required jars exist.
- `murder-mystery-hub`, `murder-mystery-game`, and `build` wait for healthy `mongo`, `redis`, and `control-panel`.
- `velocity` waits for healthy `murder-mystery-hub` and `murder-mystery-game`.
- `control-panel` `/readyz` validates Docker daemon ping, Compose project detection, and Mongo ping.

## Rollout and `/update`

- Proxy `/update` sends a webhook request to:
  - `ROLLOUT_WEBHOOK_URL` (default `http://control-panel:8080/restart`)
- Optional webhook auth header:
  - `X-Rollout-Token` from `ROLLOUT_WEBHOOK_TOKEN`

### Rollout target selection defaults

- If `ROLLOUT_INCLUDE_SERVICES` and `ROLLOUT_INCLUDE_PREFIXES` are unset, restart targets default to all non-excluded Compose services (`murder-mystery-hub`, `murder-mystery-game`, `build`, `velocity`).
- Service order default:
  - `ROLLOUT_RESTART_SERVICE_ORDER=murder-mystery-hub,murder-mystery-game,build,velocity`

### Rollout mode defaults

- `ROLLOUT_RESTART_MODE=rebuild` (Compose default in this repo)
- Valid values: `restart`, `recreate`, `rebuild`

### Rollout safety defaults

- `ROLLOUT_MIN_HUB_REPLICAS=2`
- `ROLLOUT_MIN_GAME_REPLICAS=4`
- `ROLLOUT_RESTART_HEALTH_WAIT_SECONDS=180`
- `ROLLOUT_RESTART_TIMEOUT_SECONDS=10`

### Plugin sync + validation

- control-panel syncs plugin jars from `docker/development/plugins/` into `docker/production/plugins/` and validates required plugin jars in `docker/production/plugins/` right before rollout.
- `velocity` restart is skipped unless at least one healthy hub container and one healthy game container exist.

### `/update` behavior

- Before restart/recreate/rebuild, control-panel auto-syncs fresh jars from `docker/development/plugins/` into `docker/production/plugins/`.
- `restart`: restart each target container in-place.
- `recreate`: `docker compose up -d --no-deps --force-recreate` per target service.
- `rebuild`: same as `recreate` plus `--build`.
- Service order follows `ROLLOUT_RESTART_SERVICE_ORDER`.
- Each restarted target is waited for running/healthy state before continuing.

## Build World Archive and Plugin Bootstrap

- Build container supports archive import at boot.
- Set these `.env` keys:
  - `BUILD_WORLD_ARCHIVE_URL`
  - `BUILD_WORLD_ARCHIVE_FORCE_EXTRACT` (`true`/`false`)
- Build world data persists in the named volume `build-data` (`/data`).
- Hub and game servers auto-download `Citizens.jar` by default for hub/game NPC workflows.
- Citizens source default:
  - Citizens 2.0.30 build `b2924`
- Override with:
  - `CITIZENS_URL`
- Build servers (`SERVER_TYPE=BUILD`) auto-download:
  - `Multiverse-Core.jar`
  - `WorldEdit.jar`
  - `VoxelSniper.jar`
  - `VoidGenerator.jar`

## Restart Behavior

Paper bootstrap enforces:

```text
spigot.yml: settings.restart-script: /usr/local/bin/paper-restart.sh
spigot.yml: settings.bungeecord: true
server.properties: online-mode=false
server.properties: allow-nether=false
bukkit.yml: settings.allow-end=false
```

`paper-restart.sh` terminates PID 1 so Docker restarts that container instance.
