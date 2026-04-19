package lifeledger.stocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StockStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<StockStoreData>() {}.getType();

    private final Path storePath;
    private final Map<UUID, Integer> stocks = new HashMap<>();
    private final Map<UUID, String>  names  = new HashMap<>();

    public StockStore() {
        this.storePath = FabricLoader.getInstance()
                .getConfigDir()
                .resolve("lifeledger-stocks.json");
    }

    public StockStore(Path storePath) {
        this.storePath = storePath;
    }

    public void load() {
        stocks.clear();
        names.clear();

        if (!Files.exists(storePath)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(storePath)) {
            StockStoreData data = GSON.fromJson(reader, DATA_TYPE);

            if (data == null || data.players == null) {
                save();
                return;
            }

            for (Map.Entry<String, Integer> entry : data.players.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    if (entry.getValue() != null) stocks.put(uuid, entry.getValue());
                } catch (IllegalArgumentException ignored) {}
            }

            if (data.names != null) {
                for (Map.Entry<String, String> entry : data.names.entrySet()) {
                    try {
                        UUID uuid = UUID.fromString(entry.getKey());
                        if (entry.getValue() != null) names.put(uuid, entry.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load StockStore from " + storePath, e);
        }
    }

    public void save() {
        StockStoreData data = new StockStoreData();
        for (Map.Entry<UUID, Integer> entry : stocks.entrySet())
            data.players.put(entry.getKey().toString(), entry.getValue());
        for (Map.Entry<UUID, String> entry : names.entrySet())
            data.names.put(entry.getKey().toString(), entry.getValue());

        try {
            Files.createDirectories(storePath.getParent());
            try (Writer writer = Files.newBufferedWriter(storePath)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save StockStore to " + storePath, e);
        }
    }

    public boolean hasPlayer(UUID uuid)            { return stocks.containsKey(uuid); }
    public int     getStocks(UUID uuid)             { Integer v = stocks.get(uuid); return v == null ? 0 : v; }
    public void    setStocks(UUID uuid, int amount) { stocks.put(uuid, amount); }
    public void    setName(UUID uuid, String name)  { names.put(uuid, name); }
    public String  getName(UUID uuid)               { return names.getOrDefault(uuid, ""); }

    public void removePlayer(UUID uuid) {
        stocks.remove(uuid);
        names.remove(uuid);
    }

    public Map<UUID, Integer> getAllStocks() { return Map.copyOf(stocks); }
    public Map<UUID, String>  getAllNames()  { return Map.copyOf(names); }

    private static class StockStoreData {
        Map<String, Integer> players = new HashMap<>();
        Map<String, String>  names   = new HashMap<>();
    }
}
