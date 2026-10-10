# SkyblockDB

Fabric client mod (mod id `skyblock-db`, Java package `skyblockdb`) for Hypixel SkyBlock. It records the player's
items, sacks, storage and currencies from SkyblockAPI into a local MVStore (`h2-mvstore`) and serves them to other
mods through a plain-Java API. Shared rules (commits, versioning, Hypixel rules, resource light) are in the parent
`Games\Minecraft\CLAUDE.md`. This file only has what differs.

## Builds: one jar per Minecraft version
Unlike the shared default, each Minecraft version gets its own jar, bundling only its own SkyblockAPI build. That
saves about 5 MB per jar.
- `./gradlew build` builds for 26.2 (the `mc` default in `gradle.properties`). `./gradlew build -Pmc=26.3` builds
  for 26.3.
- Per-version settings (Minecraft, Fabric API, the `fabric.mod.json` range) are in `versions/<mc>.properties`.
  A new Minecraft version needs a new file there.
- Jars are named `skyblock-db-<mod_version>+<mc>.jar`. After a bump, build every version so `build/libs` has a jar
  for each.
- Code is shared, so only use Minecraft and SkyblockAPI members present in every supported version. SkyblockAPI
  4.2.37 lacks `WardrobeAPI` and `RepoStatusEvent` on 26.3 (use `ArmorWardrobeAPI`/`EquipmentWardrobeAPI` and
  `RepoEvent`).

## Design
- The store is internal. Other mods use the public API, never the store file. A JSON export for outside tools
  is planned.
- The client thread never touches the store. Writes go through the single writer thread.
- Plan: `C:\Users\thoma\.claude\plans\ok-so-this-db-luminous-snowflake.md`.
