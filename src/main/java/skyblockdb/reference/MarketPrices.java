package skyblockdb.reference;

import skyblockdb.SkyblockApiSafe;
import skyblockdb.record.SkyblockIds;
import tech.thatgravyboat.skyblockapi.api.remote.hypixel.pricing.BazaarAPI;
import tech.thatgravyboat.skyblockapi.api.remote.hypixel.pricing.LowestBinAPI;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bazaar and lowest-BIN prices from SkyblockAPI's own downloads (it fetches both at start and every two hours; nothing
 * is downloaded here). Its lookups scan every product, so each download is indexed once by canonical ID instead.
 */
public final class MarketPrices {
    /** Canonical ID to {sell, buy}. */
    private static volatile Map<String, double[]> bazaar = Map.of();
    private static volatile Object bazaarSource;
    private static volatile Map<String, Double> lowestBin = Map.of();
    private static volatile Object lowestBinSource;

    private MarketPrices() {}

    /** {sell, buy} for a canonical ID, or null. */
    public static double[] bazaar(String sbId) {
        return SkyblockApiSafe.get("bazaar", () -> {
            List<BazaarAPI.BazaarProduct> products = BazaarAPI.INSTANCE.getProducts();
            if (products != bazaarSource) {
                Map<String, double[]> index = new HashMap<>();
                for (BazaarAPI.BazaarProduct p : List.copyOf(products)) {
                    index.put(SkyblockIds.normalize(p.getProductId()), new double[]{p.getSellPrice(), p.getBuyPrice()});
                }
                bazaar = Map.copyOf(index);
                bazaarSource = products;
            }
            return bazaar.get(sbId);
        }, null);
    }

    /** Lowest BIN for a canonical ID, or null. */
    public static Double lowestBin(String sbId) {
        return SkyblockApiSafe.get("lowest-bin", () -> {
            Map<String, LowestBinAPI.AuctionItem> items = LowestBinAPI.INSTANCE.getItems();
            if (items != lowestBinSource) {
                Map<String, Double> index = new HashMap<>();
                for (Map.Entry<String, LowestBinAPI.AuctionItem> e : Map.copyOf(items).entrySet()) {
                    index.put(SkyblockIds.normalize(e.getKey()), (double) e.getValue().getLowest());
                }
                lowestBin = Map.copyOf(index);
                lowestBinSource = items;
            }
            return lowestBin.get(sbId);
        }, null);
    }
}
