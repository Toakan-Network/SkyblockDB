package skyblockdb.api;

import java.time.Instant;

/**
 * One item in the current profile's Forge, as of the last time its menu was open (API_VERSION 7).
 *
 * @param sbId   canonical SkyBlock ID of what is being made
 * @param slot   the Forge slot
 * @param doneAt when it finishes; in the past while it waits to be collected
 */
public record ForgeItem(String sbId, int slot, Instant doneAt) {
}
