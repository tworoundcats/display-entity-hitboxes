package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LithiumCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("DisplayHitboxes");
    private static final String KEY = "mixin.entity.collisions";
    private static volatile String message;

    private LithiumCheck() {}

    public static String message() {
        return message;
    }

    public static void run() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("lithium")) return;

        Path file = loader.getConfigDir().resolve("lithium.properties");
        try {
            List<String> lines = Files.exists(file) ? new ArrayList<>(Files.readAllLines(file)) : new ArrayList<>();

            int index = -1;
            boolean disabled = false;
            for (int i = 0; i < lines.size(); i++) {
                String t = lines.get(i).replace(" ", "");
                if (t.startsWith("#")) continue;
                if (t.startsWith(KEY + "=")) {
                    index = i;
                    disabled = t.equalsIgnoreCase(KEY + "=false");
                }
            }
            if (disabled) return;

            if (DisplayHitboxConfig.get().autoFixLithium) {
                if (index >= 0) lines.set(index, KEY + "=false");
                else lines.add(KEY + "=false");
                Files.write(file, lines);
                message = "Display Entity Hitboxes: Lithium's entity collision changes WILL break display collision. "
                        + "Lithium configs were changed, Restart the game to apply it.";
            } else {
                message = "Display Hitboxes: Lithium breaks display collision. Set " + KEY
                        + "=false in config/lithium.properties and restart the game.";
            }
        } catch (IOException e) {
            message = "Display Hitboxes: Lithium breaks display collision. Set " + KEY
                    + "=false in config/lithium.properties and restart the game.";
        }
        LOGGER.warn(message);
    }
}