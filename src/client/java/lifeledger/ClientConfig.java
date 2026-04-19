package lifeledger;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClientConfig {
    private static final Gson GSON = new Gson();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
        .getConfigDir().resolve("lifeledger-client.json");

    public boolean showStocksInTabList = true;

    private static ClientConfig instance = new ClientConfig();

    public static ClientConfig get() {
        return instance;
    }

    public static void load() {
        if (!Files.exists(CONFIG_PATH)) {
            instance = new ClientConfig();
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
            instance = loaded != null ? loaded : new ClientConfig();
        } catch (IOException e) {
            LoggerFactory.getLogger(Lifeledger.MOD_ID).warn("[LifeLedger] Failed to load client config: {}", e.getMessage());
            instance = new ClientConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(Lifeledger.MOD_ID).warn("[LifeLedger] Failed to save client config: {}", e.getMessage());
        }
    }
}