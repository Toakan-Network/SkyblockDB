# Security scan: SkyblockDB

Static malware scan of the release jars and the library they bundle, run on 2026-10-11.

| Jar | SHA-256 | Confirmed behaviors | High | Medium | Report |
|---|---|---|---|---|---|
| `skyblock-db-0.5.0+26.2.jar` | `ad4986e01416c82208491b2a677309a960ca582bf82af0ef6bd397b2627434a0` | 0 | 0 | 0 | [HTML](skyblock-db-0.5.0+26.2.html), [JSON](skyblock-db-0.5.0+26.2.json) |
| `skyblock-db-0.5.0+26.3.jar` | `763bf9bee9ecb9f4643844fc01c3801561f2fe20ebecd59b712bb0e99727f86b` | 0 | 0 | 0 | [HTML](skyblock-db-0.5.0+26.3.html), [JSON](skyblock-db-0.5.0+26.3.json) |
| `skyblock-api-4.2.37-26.2.jar` (nested in the 26.2 jar) | `110f37e0ec653b48fc085204a5995ce4fee9dc551a5d630bf973bfed9408648b` | 0 | 7 (false positive, see below) | 0 | [HTML](skyblock-api-4.2.37-26.2.html), [JSON](skyblock-api-4.2.37-26.2.json) |
| `skyblock-api-4.2.37-26.3.jar` (nested in the 26.3 jar) | `071c5164bc0114a9b2a61e49e0e698b4cd6a47f8740021573d443a0c9f3a3ddd` | 0 | 7 (false positive, see below) | 0 | [HTML](skyblock-api-4.2.37-26.3.html), [JSON](skyblock-api-4.2.37-26.3.json) |

Compare the SHA-256 with your copy of the jar (`certutil -hashfile <jar> SHA256` on Windows, `sha256sum <jar>`
elsewhere). These results only hold for a jar with the same hash.

## Notes on the flagged items

- **SkyblockDB's own code** makes no network requests and has only low-severity matches (see the false positives
  listed below).
- **`obf_lambdametafactory_token_access` (high) in SkyblockAPI** (`ElectionAPI`, `TrophyFishingAPI`,
  `EventListeners`, `BazaarAPI`, `LowestBinAPI`, `SkyBlockPetsRepo`, `utils/http/Http`): this rule fires on any
  class that mentions `LambdaMetafactory`, which is how ordinary Kotlin and Java lambdas compile, so the decompiler
  shows it everywhere. The rule does not check for tokens or sessions, and Java-Triage's exfiltration analysis for
  this jar is `not_detected`.
- **SkyblockAPI's endpoints** are public data, fetched with plain GETs: `api.hypixel.net/v2/resources/skyblock/*`
  (election, items, skills), `api.hypixel.net/v2/skyblock/bazaar`, `skyblock-pv.thatgravyboat.tech/auctions`
  (lowest-BIN prices, run by SkyblockAPI's author) and the `meowdding/hypixel-pack` repo on GitHub. SkyblockAPI is
  the open-source library [SkyBlockAPI](https://github.com/SkyblockAPI/SkyblockAPI) by ThatGravyBoat and team.

## How the scan was run

- Tool: [cev-api/Java-Triage](https://github.com/cev-api/Java-Triage) at commit `f2ed39e` (2026-09-28), a static
  triage scanner for suspicious Java jars and Minecraft mods. It decompiles the jar and looks for obfuscation,
  token/session theft, webhooks, C2 endpoints and known malware families. It never runs the scanned code.
- Decompiler: CFR 0.152 (`cfr-0.152.jar`, SHA-256 `f686e8f3ded377d7bc87d216a90e9e9512df4156e75b06c655a16648ae8765b2`).
- Network off: nothing was uploaded or looked up online.

```
python java_triage.py <jar> --no-network --no-decompiler-download --decompiler cfr --no-progress --out <name>.json --html-out <name>.html
```

Java-Triage only scans a jar's own classes, not the library jars nested in `META-INF/jars/`, so each nested jar was
pulled out and scanned on its own.

Each scan has an HTML report (open it in a browser: sortable tables, source excerpts) and the full JSON output.
In the JSON, the local scan folder is replaced with `<scan-dir>`; nothing else was changed.

## How to read the results

Java-Triage sorts what it finds into tiers. `confirmed_behavior` means it saw malicious code in action (for example,
a token being read and sent out). `suspicious_capability` means the code *could* do something sensitive and is worth
a look, and these rules match broadly. Every scan below has **0 confirmed behaviors** and **0 known malware variants**.
The flagged capabilities were checked by hand against the source; the notes say what each one actually is.

Common false positives in these reports:
- **`minecraft_username_access` / `minecraft_session_access` / `minecraft_uuid_access` (low):** the rule matches
  any file containing the text `name` (or a `getUser()` / `getId()` call), such as `File.getName()`, a JSON field
  called `"name"` or reading the player for the SkyBlock profile folder. None of these mods send the player's name, UUID or session anywhere.
- **`credential_or_identity_field`:** field and method names like `name`, `profile` or `uuid`.
- **`dynamic_execution`:** Brigadier's `.executes(...)` in client command registration, not `Runtime.exec`.
