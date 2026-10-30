package skyblockdb.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectionReaderTest {
    @Test
    void forgeIdKeepsOnlyPlainItems() {
        assertEquals("TITANIUM_RING", CollectionReader.forgeId("item:titanium_ring"));
        assertEquals("", CollectionReader.forgeId("pet:bee"));
    }
}
