package skyblockdb;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every lang key the code names is in {@code en_us.json}, and every key there is used, so no screen shows a raw
 * key. Helpers that add {@code .desc} or {@code .button} to a key count as using those keys.
 */
class LangKeysTest {
    private static final String MOD_ID = SkyblockDb.MOD_ID;
    private static final Pattern KEY = Pattern.compile("\"((?:key\\.(?:category\\.)?)?" + Pattern.quote(MOD_ID)
        + "\\.[a-z0-9_.]*[a-z0-9_])\"");
    private static final Set<String> SUFFIXES = Set.of(".desc", ".button");

    @Test
    void codeAndLangFileAgree() throws IOException {
        Path root = projectRoot();
        Set<String> used = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) used.add(m.group(1));
            }
        }
        Path lang = root.resolve("src/main/resources/assets/" + MOD_ID + "/lang/en_us.json");
        JsonObject json = JsonParser.parseString(Files.readString(lang)).getAsJsonObject();
        Set<String> defined = new TreeSet<>(json.keySet());

        Set<String> missing = new TreeSet<>(used);
        missing.removeAll(defined);
        assertEquals(Set.of(), missing, "keys used in code but missing from en_us.json");

        Set<String> unused = new TreeSet<>();
        for (String key : defined) {
            if (used.contains(key)) continue;
            boolean viaHelper = SUFFIXES.stream().anyMatch(s -> key.endsWith(s)
                && used.contains(key.substring(0, key.length() - s.length())));
            if (!viaHelper) unused.add(key);
        }
        assertEquals(Set.of(), unused, "keys in en_us.json that no code uses");
    }

    /** The folder holding {@code src/main}; tests may run from a subproject below it. */
    private static Path projectRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("src/main/java"))) return dir;
        }
        assertTrue(false, "src/main/java not found above " + Path.of("").toAbsolutePath());
        return null;
    }
}
