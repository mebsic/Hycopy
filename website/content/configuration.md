Keep credentials in `.env` and shared defaults in `docker/production/config.json`. The Paper and Velocity entrypoints combine them when services start.

Start with the [environment template](#environment) below.

## Environment

Create a `.env` file in the repository root using these placeholders:

```env
# Image versions
MONGO_VERSION=8.2
REDIS_VERSION=8.6

# MongoDB
MONGO_ROOT_USERNAME=your_mongo_root_user
MONGO_ROOT_PASSWORD=your_mongo_root_password
MONGO_APP_DATABASE=your_app_database
MONGO_APP_USERNAME=your_app_mongo_user
MONGO_APP_PASSWORD=your_app_mongo_password

# Redis
REDIS_PASSWORD=your_redis_password
REDIS_DATABASE=0

# Deployment settings
ROLLOUT_WEBHOOK_TOKEN=your_rollout_webhook_token
ROLLOUT_RESTART_MODE=rebuild

# Network binding
MONGO_BIND_IP=127.0.0.1
MONGO_PORT=27017
REDIS_BIND_IP=127.0.0.1
REDIS_PORT=6379
```

Use strong unique values locally and never commit real secrets.

## Runtime Config

Configuration is split between:

- `.env` for secrets and runtime environment values.
- `docker/production/config.json` for shared non-secret defaults.

When it starts:

- Paper copies config to `/data/plugins/Hycopy/config.json`.
- Velocity copies config to `/server/plugins/hycopyproxy/config.json`.
- Entry points override Mongo/Redis connection fields in copied config from `.env`.

Do not keep real credentials in `docker/production/config.json`.

## Common Config Fields in `docker/production/config.json`

- Menus refresh cadence:
  - `menus.registryDataRefreshTicks`
  - `menus.gameMenuRefreshTicks`
  - `menus.lobbySelectorRefreshTicks`
  - `menus.lobbySelectorDataRefreshTicks`
- MOTD source:
  - `motd.collection`
  - `motd.documentId`
  - `motd.field`
  - `motd.cacheTtlSeconds`
- Proxy registry settings:
  - `proxy.registryCollection`
  - `proxy.registryGroup`
  - `proxy.registryRefreshSeconds`
  - `proxy.registryStaleSeconds`

## Secure MongoDB Compass Access (VPS)

Recommended: keep Mongo private and use SSH tunneling.

1. Keep:

   ```text
   MONGO_BIND_IP=127.0.0.1
   ```

2. Open tunnel from your machine:

   ```bash
   ssh -L 27017:127.0.0.1:27017 <user>@<vps-host>
   ```

3. In MongoDB Compass:
   - Host: `127.0.0.1`
   - Port: `27017`
   - Username/password: `MONGO_APP_USERNAME` / `MONGO_APP_PASSWORD` from `.env`
   - Authentication Database: `MONGO_APP_DATABASE`

Note: Mongo credentials are embedded in a URI for container connections; use URL-safe password characters or percent-encode special characters.

## Mongo Collections Created on Start

- Core plugin ensures:
  - `profiles`
  - `punishments`
  - `murdermystery_knife_skins`
  - `murdermystery_role_chances`
  - `murdermystery_knife_menu_state`
  - `murdermystery_information`
  - `server_registry`
  - `boss_bar_messages`
  - `rank_gift_history`
  - `chat_messages`
  - `maps`
  - `proxy_settings`

- Proxy plugin ensures:
  - configured MOTD collection (default `proxy_settings`)
  - configured friends collection (default `friends`)
  - configured registry collection (default `server_registry`)
  - `profiles`
  - `maps`
  - `autoscale`
  - `chat_messages`

- Proxy seeds the MOTD document on first run using configured `motd.documentId` (default `motd`).
