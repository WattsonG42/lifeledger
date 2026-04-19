package lifeledger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class StockCache {
    private record State(Map<UUID, Integer> stocks, Map<UUID, String> names, int maxStocks) {}

    private static volatile State state = new State(Collections.emptyMap(), Collections.emptyMap(), 0);

    private StockCache() {}

    public static void update(Map<UUID, Integer> stocks, Map<UUID, String> names, int maxStocks) {
        state = new State(Map.copyOf(stocks), Map.copyOf(names), maxStocks);
    }

    public static int getStocks(UUID uuid) {
        Integer v = state.stocks().get(uuid);
        return v != null ? v : -1;
    }

    public static int getMaxStocks() {
        return state.maxStocks();
    }

    public static Map<UUID, Integer> getAllStocks() { return state.stocks(); }
    public static Map<UUID, String>  getAllNames()  { return state.names(); }

    public static void applyDelta(UUID uuid, int stocks, boolean eliminated) {
        State current = state;
        HashMap<UUID, Integer> updatedStocks = new HashMap<>(current.stocks());
        HashMap<UUID, String>  updatedNames  = new HashMap<>(current.names());
        if (eliminated) {
            updatedStocks.remove(uuid);
            updatedNames.remove(uuid);
        } else {
            updatedStocks.put(uuid, stocks);
        }
        state = new State(Map.copyOf(updatedStocks), Map.copyOf(updatedNames), current.maxStocks());
    }

    public static void clear() {
        state = new State(Collections.emptyMap(), Collections.emptyMap(), 0);
    }
}
