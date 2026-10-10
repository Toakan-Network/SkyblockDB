package skyblockdb.store;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import skyblockdb.SkyblockDb;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One table of the store: a sorted in-memory map, kept in a JSON file next to the others. Writer thread only.
 *
 * <p>Two kinds:
 * <ul>
 *   <li>Snapshot tables ({@code <name>.json}) are rewritten whole when they changed: written to a temporary file,
 *       then moved over the old one, so a crash leaves either the old or the new file, never a broken one.</li>
 *   <li>Log tables ({@code <name>.jsonl}, one {@code [key, value]} per line) only ever gain entries: new ones are
 *       appended. A line cut off by a crash is skipped when loading.</li>
 * </ul>
 * Values are strings, numbers or booleans, or arrays of them ({@code Object[]}). Whole numbers come back as
 * {@link Long}, numbers with a decimal point or exponent as {@link Double}.
 */
final class Table<V> extends TreeMap<String, V> {
    private static final Gson GSON = new Gson();
    private final String name;
    private final boolean log;
    private boolean dirty;
    /** Log tables: entries added since the last save, in order. */
    private final List<Map.Entry<String, V>> appended = new ArrayList<>();

    Table(String name, boolean log) {
        this.name = name;
        this.log = log;
    }

    private Path file(Path dir) {
        return dir.resolve(name + (log ? ".jsonl" : ".json"));
    }

    // Change tracking.

    @Override
    public V put(String key, V value) {
        dirty = true;
        if (log) appended.add(Map.entry(key, value));
        return super.put(key, value);
    }

    @Override
    public V putIfAbsent(String key, V value) {
        V old = get(key);
        if (old == null) put(key, value);
        return old;
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> map) {
        for (Map.Entry<? extends String, ? extends V> e : map.entrySet()) put(e.getKey(), e.getValue());
    }

    @Override
    public V remove(Object key) {
        if (log) throw new UnsupportedOperationException(name + " only gains entries");
        if (containsKey(key)) dirty = true;
        return super.remove(key);
    }

    @Override
    public void clear() {
        if (log) throw new UnsupportedOperationException(name + " only gains entries");
        if (!isEmpty()) dirty = true;
        super.clear();
    }

    /** Empties the table and deletes its file (for a log that is switched off). */
    void discard(Path dir) throws IOException {
        super.clear();
        appended.clear();
        dirty = false;
        Files.deleteIfExists(file(dir));
    }

    boolean dirty() {
        return dirty;
    }

    /** The table's name (a map's own toString would list every entry). */
    @Override
    public String toString() {
        return name;
    }

    // Loading and saving.

    @SuppressWarnings("unchecked")
    void load(Path dir) throws IOException {
        Path file = file(dir);
        Files.deleteIfExists(dir.resolve(file.getFileName() + ".tmp")); // left over from a crash mid-save
        if (!Files.isRegularFile(file)) return;
        if (log) {
            int skipped = 0;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonArray entry = JsonParser.parseString(line).getAsJsonArray();
                        super.put(entry.get(0).getAsString(), (V) decode(entry.get(1)));
                    } catch (RuntimeException e) {
                        skipped++; // a line cut off by a crash
                    }
                }
            }
            if (skipped > 0) SkyblockDb.LOGGER.warn("SkyblockDB: skipped {} unreadable line(s) in {}", skipped, file.getFileName());
        } else {
            JsonObject root;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            }
            for (Map.Entry<String, JsonElement> e : root.entrySet()) super.put(e.getKey(), (V) decode(e.getValue()));
        }
    }

    void save(Path dir) throws IOException {
        if (!dirty) return;
        Path file = file(dir);
        if (log) {
            try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                for (Map.Entry<String, V> e : appended) {
                    JsonArray entry = new JsonArray();
                    entry.add(e.getKey());
                    entry.add(encode(e.getValue()));
                    out.write(entry.toString());
                    out.write('\n');
                }
            }
            appended.clear();
        } else {
            Path tmp = dir.resolve(file.getFileName() + ".tmp");
            try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8);
                 JsonWriter json = new JsonWriter(out)) {
                json.beginObject();
                for (Map.Entry<String, V> e : entrySet()) {
                    json.name(e.getKey());
                    GSON.toJson(encode(e.getValue()), json);
                }
                json.endObject();
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        dirty = false;
    }

    private static JsonElement encode(Object value) {
        if (value instanceof Object[] array) {
            JsonArray out = new JsonArray();
            for (Object o : array) out.add(encode(o));
            return out;
        }
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Double d) return new JsonPrimitive(d);
        if (value instanceof Number n) return new JsonPrimitive(n.longValue());
        throw new IllegalArgumentException("Unsupported value " + value);
    }

    private static Object decode(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            Object[] out = new Object[array.size()];
            for (int i = 0; i < out.length; i++) out[i] = decode(array.get(i));
            return out;
        }
        JsonPrimitive p = element.getAsJsonPrimitive();
        if (p.isBoolean()) return p.getAsBoolean();
        if (p.isString()) return p.getAsString();
        String text = p.getAsString();
        return text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0
            ? (Object) p.getAsDouble() : (Object) p.getAsLong();
    }
}
