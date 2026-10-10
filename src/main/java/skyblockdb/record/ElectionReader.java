package skyblockdb.record;

import kotlin.Unit;
import net.minecraft.client.Minecraft;
import skyblockdb.ApiImpl;
import skyblockdb.api.Candidate;
import skyblockdb.api.Election;
import skyblockdb.api.Perk;
import tech.thatgravyboat.skyblockapi.api.SkyBlockAPI;
import tech.thatgravyboat.skyblockapi.api.area.hub.ElectionAPI;
import tech.thatgravyboat.skyblockapi.api.data.MayorCandidate;
import tech.thatgravyboat.skyblockapi.api.data.MayorPerk;
import tech.thatgravyboat.skyblockapi.api.events.info.MayorChangeEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Mayor and minister, read live from SkyblockAPI's election tracking (it keeps them between sessions itself), so
 * nothing is stored here. Listeners hear about a change through {@code onElectionChanged}.
 */
public final class ElectionReader {
    private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private ElectionReader() {}

    public static void register() {
        SkyBlockAPI.getEventBus().register(MayorChangeEvent.class, 0, false, event -> {
            Minecraft.getInstance().execute(() -> ApiImpl.INSTANCE.fire(l -> l.onElectionChanged()));
            return Unit.INSTANCE;
        });
    }

    /** Who is in office, or empty while SkyblockAPI knows neither. */
    public static Optional<Election> current() {
        Candidate mayor = candidate(ElectionAPI.INSTANCE.getMayor());
        Candidate minister = candidate(ElectionAPI.INSTANCE.getMinister());
        if (mayor == null && minister == null) return Optional.empty();
        var next = ElectionAPI.INSTANCE.getNextElection();
        return Optional.of(new Election(mayor, minister, next == null ? null : Instant.ofEpochMilli(next.toEpochMilliseconds())));
    }

    /** A perk of the mayor or minister that is in effect now. */
    public static Optional<Perk> activePerk(String id) {
        Optional<Election> election = current();
        if (election.isEmpty()) return Optional.empty();
        for (Candidate c : new Candidate[]{election.get().mayor(), election.get().minister()}) {
            if (c == null) continue;
            for (Perk perk : c.perks()) {
                if (perk.active() && perk.id().equalsIgnoreCase(id)) return Optional.of(perk);
            }
        }
        return Optional.empty();
    }

    private static Candidate candidate(MayorCandidate c) {
        if (c == null) return null;
        List<Perk> perks = new ArrayList<>();
        for (MayorPerk perk : new ArrayList<>(c.getPerks())) {
            perks.add(new Perk(Objects.toString(perk.getId(), ""), clean(perk.getPerkName()), clean(perk.getDescription()),
                perk.getActive()));
        }
        return new Candidate(Objects.toString(c.getId(), ""), clean(c.getCandidateName()), List.copyOf(perks));
    }

    private static String clean(String s) {
        return s == null ? "" : FORMATTING.matcher(s).replaceAll("").replaceAll("\\s+", " ").trim();
    }
}
