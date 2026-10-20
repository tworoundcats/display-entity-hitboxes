package net.tworoundcats.displayblockhitboxes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisplayHitboxConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static DisplayHitboxConfig instance;

    public enum Preset { LOW, DEFAULT, HIGH, ULTRA, CUSTOM }
    public Preset preset = Preset.DEFAULT;

    public boolean blockDisplayHitboxes = true;
    public boolean itemDisplayHitboxes = true;
    public boolean autoFixLithium = true;
    public boolean accurateHitboxes = true;
    public String accurateHitboxes_info = "Makes all hitboxes more accurate. Required to be enabled when dealing with custom imported models or large structures.";

    public boolean displayPhysics = true;
    public String displayPhysics_info = "Moving displays push you along and carry you. May not work on some servers.";
    public double physicsContactMargin = 0.05;
    public double physicsStandTolerance = 0.15;
    public double physicsMaxSpeed = 3.0;
    public double physicsMaxPush = 0.5;
    public boolean physicsKeepMomentum = true;

    public boolean originIndependentLighting = true;
    public String originIndependentLighting_info = "Light is sampled around the middle of the model instead of at the origin point.";

    public int blockModelResolution = 32;
    public String blockModelResolution_info = "Block models are turned into a grid (16 = 1/16th block resolution). Higher is more exact on slanted models.";

    public int itemTextureResolution = 0;
    public String itemTextureResolution_info = "Pixels checked on flat item textures. 0 = use the real texture size.";

    public int rotatedSlicesPerBlock = 16;
    public String rotatedSlicesPerBlock_info = "Slices per block of width: 16 = slices 1/16 block wide. Higher means smoother sliding.";

    public int maxRotatedSlices = 24;
    public String maxRotatedSlices_info = "Upper limit of slices for one box of a 1D horizontally rotated display.";

    public int tiltedResolutionPerBlock = 8;
    public String tiltedResolutionPerBlock_info = "Displays tilted in multiple directions are cut into a grid of cells. Resolution per block on each side.";

    public int maxTiltedResolution = 16;
    public String maxTiltedResolution_info = "Upper limit of Resolution per side for a complex tilted display.";

    public boolean dynamicMaxSlices = false;
    public String dynamicMaxSlices_info = "Dynamically scales the upper slice limits by the display's scale, allowing stretched slopes to keep fine steps.";

    public static synchronized DisplayHitboxConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    public void applyPreset(Preset p) {
        this.preset = p;
        if (p == Preset.LOW) {
            blockModelResolution = 16; rotatedSlicesPerBlock = 8; maxRotatedSlices = 16;
            tiltedResolutionPerBlock = 4; maxTiltedResolution = 8; dynamicMaxSlices = false; itemTextureResolution = 0;
        } else if (p == Preset.DEFAULT) {
            blockModelResolution = 32; rotatedSlicesPerBlock = 16; maxRotatedSlices = 24;
            tiltedResolutionPerBlock = 8; maxTiltedResolution = 16; dynamicMaxSlices = false; itemTextureResolution = 0;
        } else if (p == Preset.HIGH) {
            blockModelResolution = 64; rotatedSlicesPerBlock = 32; maxRotatedSlices = 64;
            tiltedResolutionPerBlock = 16; maxTiltedResolution = 32; dynamicMaxSlices = false; itemTextureResolution = 16;
        } else if (p == Preset.ULTRA) {
            blockModelResolution = 64; rotatedSlicesPerBlock = 64; maxRotatedSlices = 128;
            tiltedResolutionPerBlock = 32; maxTiltedResolution = 64; dynamicMaxSlices = true; itemTextureResolution = 32;
        }
    }

    public void determinePreset() {
        if (blockModelResolution == 16 && rotatedSlicesPerBlock == 8 && maxRotatedSlices == 16 &&
                tiltedResolutionPerBlock == 4 && maxTiltedResolution == 8 && !dynamicMaxSlices) preset = Preset.LOW;
        else if (blockModelResolution == 32 && rotatedSlicesPerBlock == 16 && maxRotatedSlices == 24 &&
                tiltedResolutionPerBlock == 8 && maxTiltedResolution == 16 && !dynamicMaxSlices) preset = Preset.DEFAULT;
        else if (blockModelResolution == 64 && rotatedSlicesPerBlock == 32 && maxRotatedSlices == 64 &&
                tiltedResolutionPerBlock == 16 && maxTiltedResolution == 32 && !dynamicMaxSlices) preset = Preset.HIGH;
        else if (blockModelResolution == 64 && rotatedSlicesPerBlock == 64 && maxRotatedSlices == 128 &&
                tiltedResolutionPerBlock == 32 && maxTiltedResolution == 64 && dynamicMaxSlices) preset = Preset.ULTRA;
        else preset = Preset.CUSTOM;
    }

    private static int readInt(JsonObject obj, String key, int def, int min, int max) {
        int v = def;
        try { if (obj.has(key)) v = obj.get(key).getAsInt(); } catch (Exception ignored) {}
        return Math.max(min, Math.min(max, v));
    }

    private static double readDouble(JsonObject obj, String key, double def, double min, double max) {
        double v = def;
        try { if (obj.has(key)) v = obj.get(key).getAsDouble(); } catch (Exception ignored) {}
        if (Double.isNaN(v)) v = def;
        return Math.max(min, Math.min(max, v));
    }

    private static DisplayHitboxConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("displayblockhitboxes.json");
        DisplayHitboxConfig config = new DisplayHitboxConfig();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has("displayPhysics")) config.displayPhysics = obj.get("displayPhysics").getAsBoolean();
                if (obj.has("autoFixLithium")) config.autoFixLithium = obj.get("autoFixLithium").getAsBoolean();
                if (obj.has("blockDisplayHitboxes")) config.blockDisplayHitboxes = obj.get("blockDisplayHitboxes").getAsBoolean();
                if (obj.has("itemDisplayHitboxes")) config.itemDisplayHitboxes = obj.get("itemDisplayHitboxes").getAsBoolean();
                if (obj.has("accurateHitboxes")) config.accurateHitboxes = obj.get("accurateHitboxes").getAsBoolean();
                if (obj.has("originIndependentLighting")) config.originIndependentLighting = obj.get("originIndependentLighting").getAsBoolean();
                if (obj.has("physicsKeepMomentum")) config.physicsKeepMomentum = obj.get("physicsKeepMomentum").getAsBoolean();

                config.physicsContactMargin = readDouble(obj, "physicsContactMargin", config.physicsContactMargin, 0.0, 0.5);
                config.physicsStandTolerance = readDouble(obj, "physicsStandTolerance", config.physicsStandTolerance, 0.0, 0.5);
                config.physicsMaxSpeed = readDouble(obj, "physicsMaxSpeed", config.physicsMaxSpeed, 0.1, 20.0);
                config.physicsMaxPush = readDouble(obj, "physicsMaxPush", config.physicsMaxPush, 0.05, 2.0);

                config.blockModelResolution = readInt(obj, "blockModelResolution", config.blockModelResolution, 4, 128);
                config.itemTextureResolution = readInt(obj, "itemTextureResolution", config.itemTextureResolution, 0, 128);
                config.rotatedSlicesPerBlock = readInt(obj, "rotatedSlicesPerBlock", config.rotatedSlicesPerBlock, 2, 128);
                config.maxRotatedSlices = readInt(obj, "maxRotatedSlices", config.maxRotatedSlices, 1, 1024);
                config.tiltedResolutionPerBlock = readInt(obj, "tiltedResolutionPerBlock", config.tiltedResolutionPerBlock, 2, 64);
                config.maxTiltedResolution = readInt(obj, "maxTiltedResolution", config.maxTiltedResolution, 1, 256);

                if (obj.has("dynamicMaxSlices")) config.dynamicMaxSlices = obj.get("dynamicMaxSlices").getAsBoolean();

                if (obj.has("preset")) {
                    try { config.preset = Preset.valueOf(obj.get("preset").getAsString().toUpperCase()); }
                    catch (Exception e) { config.preset = Preset.CUSTOM; }
                }
            } catch (Exception ignored) {}
        }
        config.determinePreset();
        config.save();
        return config;
    }

    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("displayblockhitboxes.json");
        try (Writer writer = Files.newBufferedWriter(path)) {
            StringBuilder out = new StringBuilder();
            boolean first = true;
            for (String line : GSON.toJson(this).split("\n")) {
                String t = line.trim();
                boolean key = t.startsWith("\"") && line.startsWith("  \"");
                if (key && !t.matches("\"[^\"]*_info\".*") && !first) out.append('\n');
                if (key) first = false;
                out.append(line).append('\n');
            }
            writer.write(out.toString());
        } catch (IOException ignored) {}
    }
}