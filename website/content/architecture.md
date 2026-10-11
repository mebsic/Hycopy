Hycopy separates the Minecraft network into focused services. Players enter through Velocity; Paper runs the hubs, games, and build worlds behind it.

## The connection flow

![Hycopy network architecture: players connect to Velocity, which routes to Paper hubs and games backed by MongoDB and Redis.](/assets/network.svg)

1. A Java Edition client connects to the proxy on port `25565`.
2. Velocity discovers backend services through MongoDB's `server_registry` heartbeats.
3. The proxy routes the player to a hub or an available game server.
4. Shared plugins use MongoDB and Redis for profiles, configuration, and coordination.

## Project modules

| Module | Responsibility |
| --- | --- |
| `core` | Shared Paper services: profiles, ranks, punishments, menus, registry, and queues |
| `proxy` | Velocity routing, parties, friends, chat channels, maintenance, and update hooks |
| `murdermystery` | Murder Mystery hub and game logic |
| `build` | Map editing and world export tools |
| `website` | This documentation site |

## Runtime services

The Docker stack includes MongoDB, Redis, the internal control-panel service, and the Minecraft servers. Only Velocity needs to receive player connections. MongoDB binds to localhost by default; Redis and the control panel have no host port mapping in Compose.

The internal `control-panel` is part of the server deployment. It coordinates restarts, readiness, and autoscaling; it is separate from the public documentation website.

See [Quickstart](/docker-quickstart) for ports and [Deployment](/deployment) for startup dependencies.

## World templates and data

Map templates are stored under `docker/production/maps/<gameKey>/<worldDirectory>/`. Map settings live in MongoDB. Paper entrypoints resolve the selected map and copy or link its world template into the container's data directory.

The build server can export templates to both production and development map directories. See [Templates](/map-templates) for the complete workflow.
