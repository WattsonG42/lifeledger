package lifeledger.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;


public class ServerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private ServerConfig config;

    public ServerConfigManager() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("lifeledger.json");
    }

    public void load() {
        try {
            if (!Files.exists(path)) {
                config = new ServerConfig();
                save();
                return;
            }

            try (Reader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, ServerConfig.class);
            }

            if (config == null) {
                config = new ServerConfig();
                save();
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());

            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save config", e);
        }
    }
    public ServerConfig getConfig() {
        return config;
    } 
}
