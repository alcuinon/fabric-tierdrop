package net.nian.tierdrop;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code config/tierdrop_config.json}, copying the bundled default the first time.
 */
public final class TierDropConfigLoader {

    private static final Gson GSON = new Gson();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("tierdrop_config.json");

    public static TierDropConfig config = new TierDropConfig();

    private TierDropConfigLoader() {}

    public static void loadConfig() {
        try {
            copyDefaultIfMissing();
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                    TierDropConfig parsed = GSON.fromJson(reader, TierDropConfig.class);
                    if (parsed != null) config = parsed;
                }
            }
        } catch (Exception e) {
            TierDrop.LOGGER.error("Error loading tierdrop config", e);
        }

        if (config == null) config = new TierDropConfig();
        if (config.tierEquipment == null) config.tierEquipment = new TierDropConfig.TierEquipment();
        config.tierEquipment.ensureLists();
        TierConfig.reload();
    }

    public static TierDropConfig.TierEquipment equipment() {
        if (config == null || config.tierEquipment == null) {
            return new TierDropConfig.TierEquipment();
        }
        config.tierEquipment.ensureLists();
        return config.tierEquipment;
    }

    private static void copyDefaultIfMissing() throws IOException {
        if (Files.exists(CONFIG_PATH)) return;

        Files.createDirectories(CONFIG_PATH.getParent());
        try (InputStream in = TierDropConfigLoader.class.getClassLoader()
                .getResourceAsStream("assets/tierdrop/config/tierdrop_config.json")) {
            if (in == null) {
                TierDrop.LOGGER.error("Default tierdrop_config.json not found in mod jar resources.");
                return;
            }
            Files.copy(in, CONFIG_PATH);
        }
    }
}
