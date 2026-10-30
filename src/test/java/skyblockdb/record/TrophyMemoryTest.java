package skyblockdb.record;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TrophyMemoryTest {
    @Test
    void emptyLiveDataKeepsWhatIsRemembered() {
        // What a wiped trophy_fish.json looks like: SkyblockAPI reports nothing for this kind.
        assertEquals(Map.of("SILVER", 5), TrophyMemory.higher(Map.of("SILVER", 5), Map.of()));
    }

    @Test
    void takesTheHigherCountOfEachTier() {
        assertEquals(Map.of("BRONZE", 9, "SILVER", 5, "GOLD", 1),
            TrophyMemory.higher(Map.of("BRONZE", 3, "SILVER", 5), Map.of("BRONZE", 9, "SILVER", 2, "GOLD", 1)));
    }

    @Test
    void rowsRoundTripInTierOrder() {
        Object[] row = TrophyMemory.toRow(Map.of("BRONZE", 1, "DIAMOND", 4));
        assertArrayEquals(new Object[] {1, 0, 0, 4}, row);
        assertEquals(Map.of("BRONZE", 1, "SILVER", 0, "GOLD", 0, "DIAMOND", 4), TrophyMemory.fromRow(row));
    }

    @Test
    void storedRowsMayComeBackAsOtherNumberTypes() {
        assertEquals(7, TrophyMemory.fromRow(new Object[] {7.0, 0L, 0, 0}).get("BRONZE"));
    }
}
