package net.nian.tierdrop;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads tierdrop_config.json from the game config directory,
 * copying the bundled default if it doesn't exist yet.
 */
public class TierDropConfigLoader {

    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("tierdrop_config.json");

    public static TierDropConfig config;

    public static void loadConfig() {
        try {
            if (!Files.exists(CONFIG_PATH)) {
                Files.createDirectories(CONFIG_PATH.getParent());
                try (InputStream in = TierDropConfigLoader.class.getClassLoader()
                        .getResourceAsStream("assets/tierdrop/config/tierdrop_config.json")) {
                    if (in != null) {
                        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
                            byte[] buf = new byte[4096];
                            int read;
                            while ((read = in.read(buf)) != -1) out.write(buf, 0, read);
                        }
                    } else {
                        TierDrop.LOGGER.error("Default tierdrop_config.json not found in mod jar resources.");
                    }
                }
            }

            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                config = new Gson().fromJson(reader, TierDropConfig.class);
            }

            if (config == null) {
                TierDrop.LOGGER.error("Failed to parse tierdrop_config.json — using empty config.");
                config = new TierDropConfig();
            }
        } catch (IOException e) {
            TierDrop.LOGGER.error("Error loading tierdrop config", e);
        }
    }
}
