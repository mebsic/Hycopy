Run Hycopy locally with Docker Compose. You will need Git, Docker with Compose, and a Java 25 JDK to build the plugins. Use `./gradlew` from the repository root; it supplies Gradle for you.

> **Before starting:** clone [mebsic/Hycopy](https://github.com/mebsic/Hycopy), create `.env` using the [environment template](/configuration#environment), and keep real credentials out of Git.

The commands below build and run the Minecraft network. They are separate from the documentation website.

## Quick Start

1. Build plugin jars:

   ```bash
   ./gradlew shadowAll
   ```

   This builds shaded jars and copies runtime plugin jars into `docker/development/plugins/`:
   - `Hycopy.jar`
   - `MurderMystery.jar`
   - `HycopyBuild.jar`
   - `HycopyProxy.jar`

2. Set secrets in `.env` (single source of truth for credentials).

3. Edit shared non-secret runtime settings in:

   ```text
   docker/production/config.json
   ```

4. Start the stack:

   ```bash
   docker compose up --build
   ```

5. Connect to:

   ```text
   localhost:25565
   ```

## Services and Ports

- `velocity` publishes `25565` to the host.
- `mongo` publishes `27017` to `${MONGO_BIND_IP:-127.0.0.1}` by default.
- `redis` is internal-only (no host port mapping in Compose).
- `control-panel` is internal-only (no host port mapping).
- `murder-mystery-hub`, `murder-mystery-game`, and `build` are backend services discovered by proxy from Mongo `server_registry` heartbeats.
- `build` uses `SERVER_ID=build`, so it registers in Mongo with `_id=build`.

## Runtime Defaults

- Paper image build default:
  - `PAPER_VERSION=1.8.8`
- If that version cannot be resolved from the Paper API, bootstrap falls back to the latest available **1.8.x** version.
- Hub defaults:
  - `SERVER_MAX_PLAYERS=100`
- Build defaults:
  - `SERVER_MAX_PLAYERS=10`
- Game default in Compose:
  - `SERVER_MAX_PLAYERS=16`
