Hycopy uses player counts and MongoDB policy documents to decide how many hubs and game servers should run. Scale-down removes servers through a drain process so normal routing stops assigning new players.

## Autoscale Behavior

- control-panel autoscale loop starts after `AUTOSCALE_STARTUP_GRACE_SECONDS`, then runs every `AUTOSCALE_INTERVAL_SECONDS`.
- Container gating can delay the first tick until at least one healthy hub-kind and one healthy game-kind service are available, or until `AUTOSCALE_STARTUP_WAIT_TIMEOUT_SECONDS` is reached.
- Policy/state/metrics/players/drain/event docs are stored in one collection (`autoscale`) with `docType`.
- control-panel ensures a policy exists for the default game type and any game types found from registry/player data.
- Player totals normally come from fresh `server_registry` heartbeats.
- Fresh `docType=players` docs can override the registry player total for a game type until `AUTOSCALE_PLAYERS_TTL_SECONDS` expires.
- Desired server counts are computed from total online players:
  - `step` increases every `playersPerStep` players, with optional `hysteresisPlayers`
  - desired hubs = `baseHub + step * hubPerStep`
  - desired games = `baseGame + step * gamePerStep`
  - desired counts are clamped between min/max policy values
- With defaults, hubs stay at 2, games start at 4, and games add 1 replica per 5 online players up to 50.
- Scale-up uses `docker compose up -d --scale <service>=<count> --no-recreate` and respects `AUTOSCALE_SCALE_UP_COOLDOWN_SECONDS`.
- Scale-down is drain-first:
  - selected servers are marked with active `docType=drain`
  - proxy marks draining servers as `DRAINING` and excludes them from normal routing
  - containers are removed when empty or when `AUTOSCALE_DRAIN_TIMEOUT_SECONDS` is reached
  - scale-down removals respect `AUTOSCALE_SCALE_DOWN_COOLDOWN_SECONDS`
- Manual control-panel endpoints are available for autoscale state, ticks, and external player updates:
  - `GET /autoscale/state`
  - `POST /autoscale/tick`
  - `POST /autoscale/players`

## Autoscale Defaults

- `AUTOSCALE_ENABLED=true`
- `AUTOSCALE_INTERVAL_SECONDS=60`
- `AUTOSCALE_STARTUP_GRACE_SECONDS=10`
- `AUTOSCALE_STARTUP_WAIT_FOR_READY_SERVICES=true`
- `AUTOSCALE_STARTUP_WAIT_TIMEOUT_SECONDS=300`
- `AUTOSCALE_DEFAULT_GAME_TYPE=murdermystery`
- `AUTOSCALE_REGISTRY_COLLECTION=server_registry`
- `AUTOSCALE_COLLECTION=autoscale`
- `AUTOSCALE_STALE_HEARTBEAT_MILLIS=120000`
- `AUTOSCALE_PLAYERS_TTL_SECONDS=180`
- `AUTOSCALE_DRAIN_TIMEOUT_SECONDS=240`
- `AUTOSCALE_SCALE_UP_COOLDOWN_SECONDS=90`
- `AUTOSCALE_SCALE_DOWN_COOLDOWN_SECONDS=300`
- `AUTOSCALE_HYSTERESIS_PLAYERS=0`
- `AUTOSCALE_POLICY_DEFAULT_PLAYERS_PER_STEP=5`
- `AUTOSCALE_POLICY_DEFAULT_HUB_PER_STEP=0`
- `AUTOSCALE_POLICY_DEFAULT_GAME_PER_STEP=1`
- `AUTOSCALE_POLICY_DEFAULT_BASE_HUB=2`
- `AUTOSCALE_POLICY_DEFAULT_BASE_GAME=4`
- `AUTOSCALE_POLICY_DEFAULT_MIN_HUB=2`
- `AUTOSCALE_POLICY_DEFAULT_MIN_GAME=4`
- `AUTOSCALE_POLICY_DEFAULT_MAX_HUB=10`
- `AUTOSCALE_POLICY_DEFAULT_MAX_GAME=50`
