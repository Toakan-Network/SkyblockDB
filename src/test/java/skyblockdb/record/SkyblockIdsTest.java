package skyblockdb.record;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkyblockIdsTest {
    @Test
    void abicaseUsesTheRepoIdOfItsModel() {
        assertEquals("ABICASE_BLUE_RED", SkyblockIds.abicase("BLUE_RED"));
        assertEquals("ABICASE_SUMSUNG_1", SkyblockIds.abicase("sumsung_1"));
    }

    @Test
    void abicaseWithoutAModelKeepsThePlainId() {
        assertEquals("ABICASE", SkyblockIds.abicase(""));
    }
}
