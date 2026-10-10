# SkyblockDB

A Fabric client mod for Hypixel SkyBlock. It keeps a local record of your items (sacks, storage, inventory, accessory
bag, equipment, vault), currencies and profiles, plus the item and recipe reference data, and shares it with other mods
through a small Java API.

SkyblockDB does the reading so other mods don't have to. It is built on [SkyblockAPI](https://github.com/SkyblockAPI/SkyblockAPI),
which reads SkyBlock data, and adds what SkyblockAPI doesn't:
- data from past sessions and other profiles, kept between game starts
- one ID spelling for every source (the item repository's)
- optionally, for debugging, a history of how your item totals changed
- reference data (items, pets, recipes) that stays current, with a changelog of what the repository changed
- a plain-Java API, so other mods need neither SkyblockAPI nor Kotlin

On its own it adds no screens or HUD; it is a data mod for other mods (and, later, outside tools).

## Requirements
- Minecraft 26.2 or 26.3. There is one jar per version: `skyblock-db-<version>+26.2.jar` or `+26.3.jar`.
- Fabric Loader 0.19.5 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.161.0 or newer
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.13.12 or newer (SkyblockAPI is written in Kotlin)
- [Hypixel Mod API](https://modrinth.com/mod/hypixel-mod-api) 1.0.2 or newer (SkyBlock detection)

SkyblockAPI is bundled.

## Installation
Put the jar for your Minecraft version and the required mods in your `mods` folder, then start the game and join
SkyBlock. Recording starts by itself.

## What it records
| Data | Where it comes from | When it updates |
| --- | --- | --- |
| Sacks | SkyblockAPI (sack menus and `[Sacks]` chat summaries) | on every sack change |
| Ender chest, backpacks, rift storage | SkyblockAPI, while you have them open | when a menu closes |
| Accessory bag, equipment (normal and rift), vault | SkyblockAPI, while you have them open | when a menu closes |
| Player inventory | the game | when a menu closes, and on sack changes |
| Purse, bank, coop bank, bits, copper, motes, sowdust, kernels, north stars, gems, soulflow | SkyblockAPI | checked once a second, in memory |
| Profile (name, type, SkyBlock level) | SkyblockAPI | on profile change |
| Items, pets, recipes (crafting, forge, shop, Kat) | SkyblockAPI's copy of the item repository | when the repository reloads |

Storage SkyblockAPI hasn't seen yet isn't known: open it once. SkyblockAPI remembers what it saw before, also while
another mod was using it, so some storage may be known from the start.

Only differences are written. SkyblockDB keeps what you have now, not a record of past amounts.

### History (off by default)
For debugging, SkyblockDB can also log each change in an item's total across all containers. It is off by default
because it only grows and nothing else needs it. It records while `/skyblockdb history on` is set (remembered between
starts) or while `/skyblockdb debug` is on (for that session only). When history is off at startup, the old log is
deleted. The first sync with history on adds one entry per item as a starting point.

### Reference data and observations
When SkyblockAPI's item repository reloads, SkyblockDB rebuilds the items and recipes and compares them with what it
has:
- New items are added, and changed items (name, rarity, type) are updated in place.
- Items the repository dropped are marked **retired**, not deleted, so your old data keeps a name.
- Every difference is logged as a reference change, with the repository version, so you can see what changed.
- The first load only stores everything, without a change for each item.

When the game shows an item ID the repository doesn't have, SkyblockDB records it as **observed**, with the display
name it saw, at most once per game session. When a later repository update adds that ID, it is marked confirmed.

## Commands
- `/skyblockdb status`: how many profiles, item rows, currency values and history entries are stored.
- `/skyblockdb item <ID>`: where an item is on your current profile, its name and how many recipes make or use it,
  e.g. `/skyblockdb item ENCHANTED_COAL`. It goes through the public API, like another mod would.
- `/skyblockdb history [on|off]`: show or switch the item history (off by default, see above).
- `/skyblockdb debug`: turn diagnostic logging in `latest.log` on or off. While it is on, history is recorded too.

## SkyBlock only
Recording runs only while the Hypixel Mod API reports you are on SkyBlock. Lobbies and other servers never change
the stored data.

## Hypixel rules
SkyblockDB is designed to stay within the [Hypixel Allowed Modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications) guidelines:
- It is read-only. It reads what SkyblockAPI and your open screens already hold, and never sends packets, chat
  messages or commands to the server. It never hides or changes a message.
- It does not automate anything: no clicking, crafting, moving items or opening menus.
- It bundles [SkyblockAPI](https://github.com/SkyblockAPI/SkyblockAPI) (MIT, by ThatGravyBoat), which reads SkyBlock
  data the same read-only way. SkyblockDB leaves SkyblockAPI as it is, so mods built on either keep all of its
  features (see [Network access](#network-access)).

As Hypixel states, every modification is used at your own risk.

## When another mod brings a different SkyblockAPI
Fabric loads only the newest SkyblockAPI that any installed mod bundles, and every mod uses that one. If it no longer
has something this SkyblockDB calls, only the affected part (sacks, storage, accessory bag, equipment, vault,
currencies, profile, election, reference data or NPC prices) switches off for that session, with one warning in
`latest.log` naming the SkyblockAPI version. Everything else keeps working, nothing crashes, and stored data stays.
`/skyblockdb status` lists what is switched off. Updating SkyblockDB brings it back.

## Where data is stored
`config/skyblock-db/` in your game folder, as JSON files: one per table (`items.json`, `item_counts.json`, ...), and
`.jsonl` logs for the reference changes and, when it is on, the history. Their layout is internal and can change between versions,
so other mods and tools should use the API, not the files. Together they stay small, a few MB.

Changes are saved within 5 seconds, and when the game closes. Each file is replaced in one step and the logs are only
appended to, so a crash loses at most the last few seconds and never leaves a broken file. Deleting the folder (with
the game closed) starts the record from scratch. Versions before 0.4.5 used a single `skyblock.mv.db` file; it is
removed on the first start, and recording starts afresh.

## Network access
SkyblockDB itself makes no web requests. The bundled SkyblockAPI does its usual ones: the item repository (a copy of
the NotEnoughUpdates repository, which SkyblockDB uses for reference data), Hypixel's public, keyless resources (item
list, election, skills, bazaar), lowest-BIN prices from its own server (`skyblock-pv.thatgravyboat.tech`), and,
through the Hypixel Mod API, a party-info request when party chat doesn't match what it knows. SkyblockDB doesn't
block or change any of them.

## For mod developers
Other mods read SkyblockDB through the `skyblockdb.api` package. It is plain Java: records, one interface and a
listener, with no dependencies, so your mod doesn't need SkyblockAPI, Kotlin or Fabric API to use it.

### Setup
1. Get `skyblock-db-<version>+<mc>-api.jar` (from a release, or `./gradlew build` here: it is in `build/libs`).
   The API jar is the same for every Minecraft version.
2. Compile against it, without bundling it:
   ```groovy
   dependencies {
       compileOnly files("libs/skyblock-db-0.4.0+26.2-api.jar")
   }
   ```
3. Declare the mod in your `fabric.mod.json`, as `depends` if your mod needs it or `suggests` if it is optional:
   ```json
   "suggests": { "skyblock-db": ">=0.4.0" }
   ```
   With `suggests`, only touch `skyblockdb.api` classes after checking
   `FabricLoader.getInstance().isModLoaded("skyblock-db")`, so your mod still loads without it.

### Reading data
```java
import skyblockdb.api.SkyblockDbApi;

SkyblockDbApi.get().ifPresent(db -> {
    long coal = db.count("ENCHANTED_COAL");                 // total across all containers, current profile
    db.counts("ENCHANTED_COAL").forEach(row ->               // one entry per slot
        LOGGER.info("{} slot {}: {}", row.container(), row.slot(), row.amount()));
    var purse = db.currencies().get("purse");                // null until first seen on this profile
    if (purse != null) LOGGER.info("Purse: {}", purse.value());
    db.item("ENCHANTED_COAL").ifPresent(item -> LOGGER.info("{} ({})", item.name(), item.rarity()));
    db.recipes("ENCHANTED_COAL").forEach(r -> LOGGER.info("{}: {}", r.type(), r.inputs()));
});
```
`get()` is empty only when SkyblockDB isn't installed or before client initialisation. Methods without a `profile`
argument use the current profile and return empty results while it isn't known (not on SkyBlock yet). Every method
also has a `profile` (SkyBlock profile UUID) variant, and `profiles()` lists every recorded profile, including ones
you aren't on now.

Reference data beyond items and recipes:
- `mob(id)`, `mobs()`, `droppedBy(sbId)`: mobs, their loot tables and what drops an item.
- `allRecipes()`, `usages(sbId)`: every recipe, and the recipes that use an item.
- `requirements(key)`: what unlocks an item's recipe, e.g. `["Coal III"]` for `ENCHANTED_COAL`.
- `attribute(id)`, `attributes()`: Hunting attributes with their shard ID and name (recipes key attributes by ID).
- `npcSellPrice(sbId)`: what an NPC pays, from Hypixel's item list.
- `election()`, `activePerk(id)`: mayor and minister, live, e.g. `activePerk("MOLTEN_FORGE")` while Cole has it.
- `location()`: island and scoreboard area, live (`onLocationChanged` when either changes).
- `price(sbId, PriceSource)`: NPC sell, bazaar sell/buy or lowest BIN, from the prices SkyblockAPI already
  downloads (see [Network access](#network-access)). Pets have no lowest BIN.

### Reacting to changes
```java
import skyblockdb.api.SkyblockDbListener;

db.addListener(new SkyblockDbListener() {
    @Override
    public void onItemsChanged(String profile, Set<String> sbIds) {
        if (sbIds.contains("ENCHANTED_COAL")) refreshMyHud();
    }
});
```
Listeners run on the Minecraft client thread right after SkyblockDB has taken in a change, and the new data is
already readable then. Keep them short. Every method has a no-op default: `onItemsChanged`, `onCurrenciesChanged`,
`onProfileChanged`, `onReferenceDataChanged`, `onElectionChanged` and `onLocationChanged`.

### History, changes and observations
These read the store, so they return a `CompletableFuture` that completes on SkyblockDB's store thread. Hop back to
the client thread before touching the game:
```java
db.history(profile, "ENCHANTED_COAL", Instant.now().minus(Duration.ofDays(7)))
  .thenAccept(points -> Minecraft.getInstance().execute(() -> showGraph(points)));
```
- `history(profile, sbId, since)`: an item's total after each change, oldest first. Empty unless the player has
  history on (see [History](#history-off-by-default)).
- `referenceChanges(since)`: what changed in the reference data (added, changed, retired, restored, removed,
  confirmed).
- `observations(openOnly)`: IDs seen in game that the repository doesn't have (yet).

### Threads
Everything except the three future-returning methods above reads from memory and is safe from any thread. Results
are immutable.

### IDs and containers
- SkyBlock IDs use NotEnoughUpdates' spelling: pets are `TYPE;tier` (`BEE;4` is a Legendary Bee, tiers 0-5 are
  Common to Mythic), item variants use a dash (`INK_SACK-3`).
- Recipe inputs also use `SKYBLOCK_COIN` and `SKYBLOCK_<CURRENCY>` for currencies, and `ID;level` for enchantments,
  potions and runes.
- Containers: `sacks`, `inventory`, `enderchest:N`, `backpack:N`, `rift_storage:N`, `accessory_bag`,
  `equipment`, `rift_equipment`, `vault`. More may be added.

### Compatibility
`SkyblockDbApi.API_VERSION` goes up when something is added (2: reference data, 3: mobs, 4: election, attributes,
requirements, NPC prices, all recipes, 5: location, bazaar and lowest-BIN prices). Members are never removed or
changed outside a major version of SkyblockDB. If you need a newer addition, check `API_VERSION` before calling it.

## Building from source
Requires Java 25.
- `./gradlew build`: the 26.2 jar and the API jar, in `build/libs`.
- `./gradlew build -Pmc=26.3`: the 26.3 jar.

Per-version settings are in `versions/<mc>.properties`.

## Known limitations
- Storage, the accessory bag, equipment and the vault are only as current as the last time their menu was open.
- Mob drops aren't in the reference data yet.
- Observations only cover unknown IDs. Display names and rarities differ from the repository too often (reforges,
  recombobulated items) to flag usefully.

## License and attribution
- Code: GPL-3.0-only (see `LICENSE`) with additional terms under GPLv3 section 7 (see `NOTICE`): keep the credits to
  the author, mark modified versions as changed, and give a modified version its own name, mod id and icon.
- The bundled SkyblockAPI keeps its own licence (MIT, see `THIRD_PARTY_NOTICES.txt`).
- Item and recipe data comes from the NotEnoughUpdates repository through SkyblockAPI's Repo-Lib; follow its license
  when reusing that data.
