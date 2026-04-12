package lifeledger;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


// caches current online players, updates when players die or join, minimal size and network overhead
public final class StockCache {
    private record State(Map<UUID, Integer> stocks, int maxStocks) {}

    private static volatile State state = new State(Collections.emptyMap(), 0);

    private StockCache() {}

    public static void update(Map<UUID, Integer> stocks, int maxStocks) {
        state = new State(Map.copyOf(stocks), maxStocks);
    }

    public static int getStocks(UUID uuid) {
        Integer v = state.stocks().get(uuid);
        return v != null ? v : -1;
    }

    public static int getMaxStocks() {
        return state.maxStocks();
    }

    public static void applyDelta(UUID uuid, int stocks, boolean eliminated) {
        State current = state;
        HashMap<UUID, Integer> updated = new HashMap<>(current.stocks());
        if (eliminated) {
            updated.remove(uuid);
        } else {
            updated.put(uuid, stocks);
        }
        state = new State(Map.copyOf(updated), current.maxStocks());
    }

    public static void clear() {
        state = new State(Collections.emptyMap(), 0);
    }
}