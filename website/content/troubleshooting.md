Start with the service that owns the problem. Check its logs before changing settings, and change one thing at a time.

## Cannot connect to the local network

The local address is `localhost:25565`. Run these commands from the repository root:

```bash
docker compose ps
docker compose logs --tail=100 velocity
```

Check that Velocity is running and port `25565` is available. If the proxy is waiting on a dependency, inspect the hub, game, and control-panel logs:

```bash
docker compose logs --tail=100 control-panel murder-mystery-hub murder-mystery-game
```

See [service startup order](/deployment#start-order) for the dependency chain.

## Missing plugin jars

The control-panel pre-start script checks for the required plugin jars. They must be present in `docker/development/plugins/` before startup or an update.

Follow the build step in [Quickstart](/docker-quickstart#quick-start). A successful build supplies `Hycopy.jar`, `MurderMystery.jar`, `HycopyBuild.jar`, and `HycopyProxy.jar`.

## Database authentication fails

Check `.env` for the MongoDB application user, password, and database name. The entrypoints use these values to override runtime connection settings. Keep MongoDB credentials out of `config.json`.

For MongoDB Compass on a VPS, follow [the SSH tunnel guide](/configuration#secure-mongodb-compass-access-vps). Passwords containing URI-reserved characters must be percent-encoded when placed in a connection URI.

## The map editor will not open

Join the build server and teleport into your target world first. `/edit` is blocked in a world named `world`.

A world whose name contains `hub` requires `MURDER_MYSTERY_HUB`. A game world without `hub` in its name requires `MURDER_MYSTERY_GAME`. See [Creation](/murdermystery/creating-maps#build-server-workflow).

## A map will not save

Move other players out of the world before selecting **Save Map**. The exporter checks that no other players remain before it unloads and copies the world.

Check the configured map roots and their filesystem permissions if the export still fails. See [the save sequence](/map-templates#saving-a-map).

## Reporting an issue

Open an issue in [mebsic/Hycopy](https://github.com/mebsic/Hycopy/issues). Include the steps to reproduce, expected and actual results, relevant service logs, and screenshots where they help. Remove credentials from logs before sharing them.
