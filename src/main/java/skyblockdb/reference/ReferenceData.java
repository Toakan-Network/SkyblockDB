package skyblockdb.reference;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kotlin.Unit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import skyblockdb.ApiImpl;
import skyblockdb.SkyblockDb;
import skyblockdb.api.Recipe;
import skyblockdb.api.ReferenceItem;
import skyblockdb.record.SkyblockIds;
import skyblockdb.store.Store;
import tech.thatgravyboat.repolib.api.PetsAPI;
import tech.thatgravyboat.repolib.api.RepoAPI;
import tech.thatgravyboat.repolib.api.RepoStatus;
import tech.thatgravyboat.repolib.api.recipes.CraftingRecipe;
import tech.thatgravyboat.repolib.api.recipes.ForgeRecipe;
import tech.thatgravyboat.repolib.api.recipes.KatRecipe;
import tech.thatgravyboat.repolib.api.recipes.ShopRecipe;
import tech.thatgravyboat.repolib.api.recipes.ingredient.AttributeIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.CraftingIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.CurrencyIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.EnchantmentIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.ItemIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.PetIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.PotionIngredient;
import tech.thatgravyboat.repolib.api.recipes.ingredient.RuneIngredient;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.events.repo.RepoEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Reference data (items, pets, recipes) from SkyblockAPI's Repo-Lib, kept current in the store.
 *
 * <p>Each time Repo-Lib (re)loads, the rows are rebuilt off-thread and hashed; when the hash differs from the stored
 * one, the writer thread diffs them against the store: new IDs are added, changed rows updated in place, IDs the
 * repository dropped are marked retired (never deleted), and every difference gets a {@code reference_changes} row.
 * The first build only sets a baseline, without change rows.
 *
 * <p>IDs seen in game that the repository doesn't have are recorded as {@code origin=observed} with an observation;
 * when a later repository update has them, they switch to {@code repo} and the observation is closed.
 */
public final class ReferenceData {
    private static final List<String> PET_TIERS = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC");
    private static final List<String> RARITIES = List.of("VERY SPECIAL", "UNCOMMON", "COMMON", "RARE", "EPIC",
        "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "ULTIMATE", "SUPREME", "ADMIN");
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static final String COIN_ID = "SKYBLOCK_COIN";
    private static final String KIND_UNKNOWN = "unknown_id";

    private static final ExecutorService BUILDER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SkyblockDB reference build");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean AGAIN = new AtomicBoolean(false);

    /** Readable views, replaced as a whole. */
    private record View(Map<String, ReferenceItem> items, Map<String, List<Recipe>> byOutput,
                        Map<String, List<Recipe>> byInput) {
        static final View EMPTY = new View(Map.of(), Map.of(), Map.of());
    }

    private static volatile View view = View.EMPTY;
    /** IDs already observed this session, so each is written at most once. Client thread only. */
    private static final Set<String> observedThisSession = new HashSet<>();
    private static int changeSequence = 0;

    private ReferenceData() {}

    /** Rebuilds whenever Repo-Lib (re)loads, and now if it already has. */
    public static void register() {
        SkyBlockAPI.getEventBus().register(RepoEvent.Reload.class, 0, false, event -> {
            if (event.getStatus() == RepoStatus.SUCCESS) buildAsync();
            return Unit.INSTANCE;
        });
        if (RepoAPI.isInitialized()) buildAsync();
    }

    // Reads (any thread).

    public static ReferenceItem item(String sbId) {
        return view.items().get(sbId);
    }

    public static Map<String, ReferenceItem> items() {
        return view.items();
    }

    public static List<Recipe> recipes(String sbId) {
        return view.byOutput().getOrDefault(sbId, List.of());
    }

    public static List<Recipe> usages(String sbId) {
        return view.byInput().getOrDefault(sbId, List.of());
    }

    /** True once reference data is readable (stored or freshly built). */
    public static boolean ready() {
        return !view.items().isEmpty();
    }

    // Loading from the store (writer thread, right after it opens).

    public static void onStoreOpened(Store store) {
        Map<String, Object[]> items = new HashMap<>(store.items());
        Map<String, Object[]> recipes = new HashMap<>(store.recipes());
        if (items.isEmpty()) return;
        publish(items, recipes, Set.of("items", "recipes"), Set.of());
    }

    // Building from Repo-Lib.

    private static void buildAsync() {
        if (!RUNNING.compareAndSet(false, true)) {
            AGAIN.set(true);
            return;
        }
        BUILDER.execute(() -> {
            try {
                do {
                    AGAIN.set(false);
                    build();
                } while (AGAIN.get());
            } catch (Throwable t) {
                SkyblockDb.LOGGER.warn("Reference build failed", t);
            } finally {
                RUNNING.set(false);
            }
        });
    }

    private static void build() {
        Map<String, Object[]> items = new TreeMap<>();
        for (Map.Entry<String, JsonObject> e : RepoAPI.items().items().entrySet()) {
            String id = SkyblockIds.normalize(e.getKey());
            if (id.isEmpty()) continue;
            JsonObject components = object(e.getValue(), "components");
            String name = clean(text(object(components, "minecraft:custom_name")));
            String[] rarityType = rarityType(loreLines(components));
            items.put(id, new Object[]{name, rarityType[0], rarityType[1], "repo", 0L});
        }
        for (Map.Entry<String, PetsAPI.Data> e : RepoAPI.pets().pets().entrySet()) {
            for (String tier : e.getValue().tiers().keySet()) {
                String id = petKey(e.getKey(), tier);
                if (id == null) continue;
                String name = clean(e.getValue().name()) + " (" + titleCase(tier) + ")";
                items.put(id, new Object[]{name, tier.toUpperCase(Locale.ROOT), "PET", "repo", 0L});
            }
        }
        if (items.isEmpty()) {
            SkyblockDb.LOGGER.warn("Repo-Lib has no items yet; keeping the stored reference data");
            return;
        }

        Map<String, List<String[]>> recipeLists = new TreeMap<>();
        for (CraftingRecipe r : RepoAPI.recipes().getRecipes(tech.thatgravyboat.repolib.api.recipes.Recipe.Type.CRAFTING)) {
            addRecipe(recipeLists, "crafting", r.result(), r.inputs(), 0, 0, "");
        }
        for (ForgeRecipe r : RepoAPI.recipes().getRecipes(tech.thatgravyboat.repolib.api.recipes.Recipe.Type.FORGE)) {
            addRecipe(recipeLists, "forge", r.result(), r.inputs(), r.coins(), r.time(), "");
        }
        for (ShopRecipe r : RepoAPI.recipes().getRecipes(tech.thatgravyboat.repolib.api.recipes.Recipe.Type.SHOP)) {
            addRecipe(recipeLists, "shop", r.result(), r.inputs(), 0, 0, Objects.toString(r.npc(), ""));
        }
        for (KatRecipe r : RepoAPI.recipes().getRecipes(tech.thatgravyboat.repolib.api.recipes.Recipe.Type.KAT)) {
            List<CraftingIngredient> inputs = new ArrayList<>(r.items());
            inputs.add(r.input());
            addRecipe(recipeLists, "kat", r.output(), inputs, r.coins(), r.time(), "");
        }
        // Several recipes for one output: numbered in sorted order, so the keys don't move between builds.
        Map<String, Object[]> recipes = new TreeMap<>();
        recipeLists.forEach((typeAndOutput, list) -> {
            list.sort((a, b) -> String.join("\u0000", a).compareTo(String.join("\u0000", b)));
            for (int i = 0; i < list.size(); i++) {
                String[] r = list.get(i);
                recipes.put(typeAndOutput + Store.SEP + i,
                    new Object[]{Long.parseLong(r[0]), r[1], Long.parseLong(r[2]), Long.parseLong(r[3]), r[4]});
            }
        });

        String hash = hash(items, recipes);
        String sha = repoSha();
        Store.get().write(store -> apply(store, items, recipes, hash, sha));
    }

    /** One recipe as [output count, inputs, coins, seconds, npc] under type|output. */
    private static void addRecipe(Map<String, List<String[]>> target, String type, CraftingIngredient output,
                                  List<CraftingIngredient> inputs, long coins, long seconds, String npc) {
        String out = key(output);
        if (out == null || inputs == null) return;
        Map<String, Long> amounts = new TreeMap<>();
        for (CraftingIngredient in : inputs) {
            String id = key(in);
            if (id != null && in.count() > 0) amounts.merge(id, (long) in.count(), Long::sum);
        }
        if (amounts.isEmpty() && coins <= 0) return;
        StringBuilder encoded = new StringBuilder();
        amounts.forEach((id, n) -> {
            if (!encoded.isEmpty()) encoded.append(';');
            encoded.append(id).append('*').append(n);
        });
        target.computeIfAbsent(type + Store.SEP + out, k -> new ArrayList<>()).add(new String[]{
            Integer.toString(Math.max(1, output.count())), encoded.toString(), Long.toString(Math.max(0, coins)),
            Long.toString(Math.max(0, seconds)), npc});
    }

    /** Ingredient key: SkyBlock ID, pets "BEE;4", currencies SKYBLOCK_X, enchantments/potions/runes ID;level. */
    private static String key(CraftingIngredient in) {
        if (in == null) return null;
        return switch (in) {
            case ItemIngredient item -> emptyToNull(SkyblockIds.normalize(item.id()));
            case PetIngredient pet -> petKey(pet.id(), pet.tier());
            case CurrencyIngredient currency -> "COIN".equals(currency.currency())
                ? COIN_ID : "SKYBLOCK_" + currency.currency().toUpperCase(Locale.ROOT);
            case EnchantmentIngredient enchantment -> SkyblockIds.normalize(enchantment.id()) + ";" + enchantment.level();
            case PotionIngredient potion -> SkyblockIds.normalize(potion.id()) + ";" + potion.level();
            case RuneIngredient rune -> SkyblockIds.normalize(rune.id()) + ";" + rune.tier();
            case AttributeIngredient attribute -> emptyToNull(SkyblockIds.normalize(attribute.id()));
            default -> null;
        };
    }

    // Applying (writer thread).

    private static void apply(Store store, Map<String, Object[]> items, Map<String, Object[]> recipes, String hash, String sha) {
        if (hash.equals(store.meta().get("reference_hash"))) {
            SkyblockDb.debug("Reference data unchanged");
            if (!ready()) publish(new HashMap<>(store.items()), new HashMap<>(store.recipes()), Set.of("items", "recipes"), Set.of());
            return;
        }
        boolean baseline = store.items().isEmpty();
        long now = System.currentTimeMillis();
        Set<String> changedItems = new HashSet<>();
        Set<String> changedRecipeIds = new HashSet<>();

        // Items.
        for (Map.Entry<String, Object[]> e : items.entrySet()) {
            String id = e.getKey();
            Object[] fresh = e.getValue();
            Object[] old = store.items().get(id);
            if (old == null) {
                store.items().put(id, fresh);
                if (!baseline) change(store, now, id, "item", "added", "", "", (String) fresh[0], sha);
                changedItems.add(id);
                continue;
            }
            boolean wasObserved = "observed".equals(old[3]);
            boolean wasRetired = ((Long) old[4]) != 0L;
            boolean differs = false;
            String[] fields = {"name", "rarity", "category"};
            for (int i = 0; i < 3; i++) {
                if (!Objects.equals(old[i], fresh[i])) {
                    differs = true;
                    // An observed item had only a seen name; confirming it is the change, not each field.
                    if (!wasObserved && !baseline) change(store, now, id, "item", "changed", fields[i], (String) old[i], (String) fresh[i], sha);
                }
            }
            if (wasObserved) {
                change(store, now, id, "item", "confirmed", "", (String) old[0], (String) fresh[0], sha);
                resolveObservations(store, id, now);
            }
            if (wasRetired) change(store, now, id, "item", "restored", "", "", (String) fresh[0], sha);
            if (differs || wasObserved || wasRetired) {
                store.items().put(id, fresh);
                changedItems.add(id);
            }
        }
        for (Map.Entry<String, Object[]> e : new ArrayList<>(store.items().entrySet())) {
            Object[] old = e.getValue();
            if (items.containsKey(e.getKey()) || !"repo".equals(old[3]) || ((Long) old[4]) != 0L) continue;
            store.items().put(e.getKey(), new Object[]{old[0], old[1], old[2], "repo", now});
            change(store, now, e.getKey(), "item", "retired", "", (String) old[0], "", sha);
            changedItems.add(e.getKey());
        }

        // Recipes: compared per type|output, as the set of its recipes.
        Map<String, List<String>> freshByOutput = groupRecipes(recipes);
        Map<String, List<String>> oldByOutput = groupRecipes(store.recipes());
        Set<String> outputs = new HashSet<>(freshByOutput.keySet());
        outputs.addAll(oldByOutput.keySet());
        for (String typeAndOutput : outputs) {
            List<String> fresh = freshByOutput.getOrDefault(typeAndOutput, List.of());
            List<String> old = oldByOutput.getOrDefault(typeAndOutput, List.of());
            if (fresh.equals(old)) continue;
            int sep = typeAndOutput.indexOf(Store.SEP);
            String type = typeAndOutput.substring(0, sep);
            String output = typeAndOutput.substring(sep + 1);
            String what = old.isEmpty() ? "added" : fresh.isEmpty() ? "removed" : "changed";
            if (!baseline) change(store, now, output, "recipe:" + type, what, "recipes", String.join(" | ", old), String.join(" | ", fresh), sha);
            changedRecipeIds.add(output);
        }
        if (!changedRecipeIds.isEmpty() || baseline) {
            store.recipes().clear();
            store.recipes().putAll(recipes);
        }

        store.meta().put("reference_hash", hash);
        if (!sha.isEmpty()) store.meta().put("repo_items_sha", sha);
        SkyblockDb.LOGGER.info("Reference data {}: {} items ({} changed), {} recipes ({} outputs changed)",
            baseline ? "stored" : "updated", items.size(), changedItems.size(), recipes.size(), changedRecipeIds.size());

        Set<String> kinds = new HashSet<>();
        if (!changedItems.isEmpty() || baseline) kinds.add("items");
        if (!changedRecipeIds.isEmpty() || baseline) kinds.add("recipes");
        Set<String> ids = new HashSet<>(changedItems);
        ids.addAll(changedRecipeIds);
        publish(new HashMap<>(store.items()), new HashMap<>(store.recipes()), kinds, baseline ? Set.of() : ids);
    }

    /** type|output -> the encoded recipes, in key order. */
    private static Map<String, List<String>> groupRecipes(Map<String, Object[]> recipes) {
        Map<String, List<String>> out = new TreeMap<>();
        new TreeMap<>(recipes).forEach((key, v) -> out.computeIfAbsent(key.substring(0, key.lastIndexOf(Store.SEP)),
            k -> new ArrayList<>()).add(v[0] + "x <- " + v[1] + " +" + v[2] + "c " + v[3] + "s " + v[4]));
        return out;
    }

    private static void change(Store store, long now, String id, String kind, String change, String field,
                               String old, String fresh, String sha) {
        String key = Store.key(String.format("%013d", now), String.format("%06d", changeSequence++ % 1_000_000));
        store.referenceChanges().put(key, new Object[]{id, kind, change, field, Objects.toString(old, ""),
            Objects.toString(fresh, ""), sha});
    }

    private static void resolveObservations(Store store, String id, long now) {
        for (Map.Entry<String, Object[]> e : Store.withPrefix(store.observations(), id)) {
            Object[] v = e.getValue();
            if (((Long) v[4]) == 0L) store.observations().put(e.getKey(), new Object[]{v[0], v[1], v[2], v[3], now});
        }
    }

    // Observations (client thread, from the recorder).

    /** IDs seen in game with their display names; unknown ones are recorded once per session. */
    public static void observe(Map<String, String> seen) {
        if (!ready() || seen.isEmpty()) return;
        Map<String, String> unknown = new LinkedHashMap<>();
        seen.forEach((id, name) -> {
            ReferenceItem known = item(id);
            if ((known == null || known.observed()) && observedThisSession.add(id)) unknown.put(id, clean(name));
        });
        if (unknown.isEmpty()) return;
        long now = System.currentTimeMillis();
        Store.get().write(store -> {
            for (Map.Entry<String, String> e : unknown.entrySet()) {
                String key = Store.key(e.getKey(), KIND_UNKNOWN);
                Object[] old = store.observations().get(key);
                store.observations().put(key, old == null
                    ? new Object[]{e.getValue(), now, now, 1L, 0L}
                    : new Object[]{e.getValue(), old[1], now, ((Long) old[3]) + 1, old[4]});
                store.items().putIfAbsent(e.getKey(), new Object[]{e.getValue(), "", "", "observed", 0L});
            }
            SkyblockDb.debug("Observed {} ID(s) not in the repository: {}", unknown.size(), unknown.keySet());
            publish(new HashMap<>(store.items()), new HashMap<>(store.recipes()), Set.of("items"), unknown.keySet());
        });
    }

    // Publishing.

    /** Builds the views from stored rows and tells listeners on the client thread. */
    private static void publish(Map<String, Object[]> items, Map<String, Object[]> recipes, Set<String> kinds, Set<String> ids) {
        Map<String, ReferenceItem> itemView = new HashMap<>();
        items.forEach((id, v) -> itemView.put(id, new ReferenceItem(id, (String) v[0], (String) v[1], (String) v[2],
            (String) v[3], ((Long) v[4]) == 0L ? null : Instant.ofEpochMilli((Long) v[4]))));
        Map<String, List<Recipe>> byOutput = new HashMap<>();
        Map<String, List<Recipe>> byInput = new HashMap<>();
        new TreeMap<>(recipes).forEach((key, v) -> {
            int first = key.indexOf(Store.SEP);
            String type = key.substring(0, first);
            String output = key.substring(first + 1, key.lastIndexOf(Store.SEP));
            Map<String, Integer> inputs = new LinkedHashMap<>();
            for (String part : ((String) v[1]).split(";")) {
                int star = part.lastIndexOf('*');
                if (star > 0) inputs.put(part.substring(0, star), Integer.parseInt(part.substring(star + 1)));
            }
            Recipe recipe = new Recipe(type, output, ((Long) v[0]).intValue(), Map.copyOf(inputs), (Long) v[2],
                ((Long) v[3]).intValue(), (String) v[4]);
            byOutput.computeIfAbsent(output, k -> new ArrayList<>()).add(recipe);
            for (String input : inputs.keySet()) byInput.computeIfAbsent(input, k -> new ArrayList<>()).add(recipe);
        });
        byOutput.replaceAll((k, v) -> List.copyOf(v));
        byInput.replaceAll((k, v) -> List.copyOf(v));
        view = new View(Map.copyOf(itemView), Map.copyOf(byOutput), Map.copyOf(byInput));
        Set<String> kindsCopy = Set.copyOf(kinds);
        Set<String> idsCopy = Set.copyOf(ids);
        Minecraft.getInstance().execute(() -> ApiImpl.INSTANCE.fire(l -> l.onReferenceDataChanged(kindsCopy, idsCopy)));
    }

    // Helpers.

    /** SHA of the repository's items file from Repo-Lib's cache, for the changelog; empty if unreadable. */
    private static String repoSha() {
        try {
            Path shas = FabricLoader.getInstance().getGameDir().resolve("skyblock-repo-cache").resolve("shas.json");
            if (!Files.isRegularFile(shas)) return "";
            JsonElement root = JsonParser.parseString(Files.readString(shas));
            if (!root.isJsonObject()) return "";
            for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
                if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("items")) {
                    return e.getValue().getAsJsonObject().get("items").getAsString();
                }
            }
        } catch (Exception e) {
            SkyblockDb.debug("Could not read the repository SHA: {}", e.toString());
        }
        return "";
    }

    private static String hash(Map<String, Object[]> items, Map<String, Object[]> recipes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map<String, Object[]> map : List.of(items, recipes)) {
                for (Map.Entry<String, Object[]> e : new TreeMap<>(map).entrySet()) {
                    digest.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                    for (Object o : e.getValue()) digest.update(("\u0000" + o).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
                digest.update((byte) 0x1E);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** "BEE" + "LEGENDARY" -> "BEE;4"; null for a tier outside Common..Mythic. */
    private static String petKey(String id, String tier) {
        if (id == null || tier == null) return null;
        int index = PET_TIERS.indexOf(tier.toUpperCase(Locale.ROOT));
        return index < 0 ? null : SkyblockIds.normalize(id) + ";" + index;
    }

    /** [rarity, type] from the last lore line ("EPIC DUNGEON HELMET" -> EPIC, DUNGEON HELMET); empty when unknown. */
    private static String[] rarityType(List<String> lore) {
        String last = null;
        for (String line : lore) {
            String c = clean(line);
            if (!c.isBlank()) last = c;
        }
        if (last != null) {
            if (last.startsWith("a ")) last = last.substring(2);
            if (last.endsWith(" a")) last = last.substring(0, last.length() - 2);
            for (String rarity : RARITIES) {
                if (last.equals(rarity) || last.startsWith(rarity + " ")) {
                    return new String[]{rarity, last.substring(rarity.length()).trim()};
                }
            }
        }
        return new String[]{"", ""};
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null) return null;
        JsonElement e = parent.get(name);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String text(JsonObject component) {
        if (component == null) return null;
        JsonElement e = component.get("text");
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static List<String> loreLines(JsonObject components) {
        List<String> out = new ArrayList<>();
        if (components == null) return out;
        JsonElement lore = components.get("minecraft:lore");
        if (lore == null || !lore.isJsonArray()) return out;
        for (JsonElement line : (JsonArray) lore) {
            if (line.isJsonObject()) out.add(text(line.getAsJsonObject()));
            else if (line.isJsonPrimitive()) out.add(line.getAsString());
        }
        return out;
    }

    private static String clean(String s) {
        if (s == null) return "";
        return FORMATTING.matcher(s).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private static String titleCase(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
