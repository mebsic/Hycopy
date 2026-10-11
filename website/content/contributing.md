Hycopy is open source. Focused bug reports, documentation improvements, and small pull requests make the project easier to maintain.

## Start with an issue

Search [existing issues](https://github.com/mebsic/Hycopy/issues) before opening a new one. For a bug, include reproduction steps, expected behavior, actual behavior, and relevant logs or screenshots. For a feature, describe its use case and scope before starting implementation.

## Keep changes focused

Fork the repository and create a branch from `main`. Follow the existing code style in `core`, `murdermystery`, `build`, and `proxy`. Keep each pull request scoped to one logical change and update the documentation when behavior changes.

Never commit real credentials. Use placeholders for `.env` examples.

## Improve the documentation

This website reads Markdown from `website/content/` and navigation from `website/content/navigation.json`. New content can be reviewed alongside the code it describes.

Edit the corresponding Markdown article directly in this repository and preview it locally. From `website/`, run `npm start` to preview and `npm test` to check your changes. Add new pages with a matching entry in `content/navigation.json`.

Attach useful screenshots to your pull request. Keep the Minecraft UI readable and show the result the reader should expect.

## Open a pull request

Link the related issue and describe what changed, why it changed, and how you checked it. Include screenshots or logs for UI and gameplay changes. Respond to review feedback and wait for review and required checks before merging.

## License and attribution

Hycopy is licensed under [GNU GPL v3.0](https://github.com/mebsic/Hycopy/blob/main/LICENSE).

This is an unofficial project. It is not affiliated with or endorsed by Hypixel Inc.
