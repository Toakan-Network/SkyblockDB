package skyblockdb.api;

import java.time.Instant;

/**
 * Who is in office (API_VERSION 4), read live from SkyblockAPI's election tracking.
 *
 * @param mayor        the mayor, or null when not known yet
 * @param minister     the minister, or null when not known yet
 * @param nextElection when the next mayor takes office, or null when not known
 */
public record Election(Candidate mayor, Candidate minister, Instant nextElection) {
}
