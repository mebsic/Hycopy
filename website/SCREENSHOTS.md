# Documentation screenshots

Capture real Hycopy interfaces using a local or staging Minecraft network. Do not replace them with AI-generated gameplay or screenshots of another server.

## Capture plan

| Guide | Useful image | Suggested filename |
| --- | --- | --- |
| Quickstart | Docker services running, followed by the successful local Minecraft connection | `docker-services.png`, `first-connection.png` |
| Creation | A build world after entering the correct edit mode | `build-world.png` |
| Editor | Hub/game editor menus and the player-spawn action | `hub-edit-menu.png`, `game-edit-menu.png`, `player-spawn.png` |
| Templates | The save result and exported world folder | `map-export.png` |
| Configuration | A sanitized example of MongoDB Compass through the local tunnel | `mongo-compass.png` |

## Style

Use a consistent Minecraft UI scale, hide unrelated chat, and keep important text readable. Use a wide gameplay image around 1600 pixels across; crop menu screenshots to the relevant interface. Prefer PNG for interface text and WebP/JPEG for large world scenes. Never include credentials, private player information, or unrelated windows.

Number annotations to match the steps in the guide. Give each image descriptive alt text and a caption that explains the result, rather than repeating its filename.

## Adding images

Put local assets in `website/public/screenshots/` and reference `/assets/screenshots/<filename>` in the corresponding Markdown article under `website/content/`. Commit the image and article together. Public HTTPS image URLs are supported too.

```markdown
<figure>
  <img src="/assets/screenshots/game-edit-menu.png" alt="Murder Mystery game edit menu with Player Spawn highlighted" loading="lazy">
  <figcaption>Select Player Spawn to add a location at your current position.</figcaption>
</figure>
```

These are suggested filenames, not assets included with the initial implementation.
