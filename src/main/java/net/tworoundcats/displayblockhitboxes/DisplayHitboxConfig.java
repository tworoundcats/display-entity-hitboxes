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

    public boolean creativeCollision = true;
    public boolean blockDisplayHitboxes = true;
    public boolean itemDisplayHitboxes = true;

    public boolean autoFixLithium = true;

    public boolean accurateHitboxes = true;
    public String accurateHitboxes_info = "This will make item and block display hitboxes vastly more accurate, but may cause performance issues with high numbers of entities. Required to be enabled when dealing with custom imported models or large structures.";

    public boolean displayPhysics = true;
    public String displayPhysics_info = "Accurate mode only. Moving displays such as animated rigs, platforms, etc.. push you along and carry you instead of just displacing you. Client side, single player only.";

    public double physicsContactMargin = 0.05;
    public String physicsContactMargin_info = "Display physics only. How close (in blocks) you have to be to a display surface to count as touching it. Higher makes you stick to moving platforms more easily. Range 0.0-0.5.";

    public double physicsStandTolerance = 0.15;
    public String physicsStandTolerance_info = "Display physics only. How far (in blocks) your feet may sink into the top of a display and still count as standing on it, so you get carried along. Range 0.0-0.5.";

    public double physicsMaxSpeed = 3.0;
    public String physicsMaxSpeed_info = "Display physics only. Surfaces moving faster than this (blocks per tick) are ignored, so teleports and animation restarts don't launch you. Range 0.1-20.0.";

    public double physicsMaxPush = 0.5;
    public String physicsMaxPush_info = "Display physics only. Most you can be pushed out of a display that moves into you, per tick (in blocks). Lower is gentler but may let you clip into fast displays. Range 0.05-2.0.";

    public boolean physicsKeepMomentum = true;
    public String physicsKeepMomentum_info = "Display physics only. When you jump or walk off a moving display, you keep its speed instead of stopping dead.";

    public boolean originIndependentLighting = true;
    public String originIndependentLighting_info = "When a display has no brightness override, its light is sampled around the middle of the model instead of at the origin point";

    public int blockModelResolution = 32;
    public String blockModelResolution_info = "Accurate mode only. Block models are turned into a grid of this resolution per block on each axis (N x N x N). 16 = 1/16 block resolution (one pixel), 32 = half a pixel, 64 = quarter pixel. Higher is more exact on slanted or rotated model parts (like amethyst) but it may cause performance issues. Range 4-64. Needs a restart.";

    public int itemTextureResolution = 0;
    public String itemTextureResolution_info = "Accurate mode only. How many pixels per side are checked on flat item textures. 0 = use the real texture size (pixel perfect, 16 for vanilla items, more for higher-res packs). A lower number gives a coarser hitbox. Range 0-128. Needs a restart.";

    public int rotatedSlicesPerBlock = 16;
    public String rotatedSlicesPerBlock_info = "Accurate mode only. A display rotated only horizontally is collided with as thin slices. This is how many slices per block of width: 16 = slices 1/16 block wide, 32 = 1/32. Higher means smoother sliding along diagonal faces but also more collision boxes. Range 2-64. Needs a restart.";

    public int maxRotatedSlices = 24;
    public String maxRotatedSlices_info = "Upper limit of slices for one box of a horizontally rotated display, so very large displays don't create thousands of boxes. Raise it for pixel perfect collision on big models. Range 1-128. Needs a restart.";

    public int tiltedResolutionPerBlock = 8;
    public String tiltedResolutionPerBlock_info = "Accurate mode only. Displays tilted in more than one direction are cut into a grid of cells instead. This is the resolution per block on each side (8 = 1/8 block resolution). This grows with the square, so keep it low unless you need it. Range 2-32. Needs a restart.";

    public int maxTiltedResolution = 16;
    public String maxTiltedResolution_info = "Upper limit of Resolution per side for one box of a tilted display (16 means at most 16 x 16 Resolution). Range 1-64. Needs a restart.";

    public static synchronized DisplayHitboxConfig get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static int readInt(JsonObject obj, String key, int def, int min, int max) {
        int v = def;
        try {
            if (obj.has(key)) v = obj.get(key).getAsInt();
        } catch (Exception ignored) {}
        return Math.max(min, Math.min(max, v));
    }

    private static double readDouble(JsonObject obj, String key, double def, double min, double max) {
        double v = def;
        try {
            if (obj.has(key)) v = obj.get(key).getAsDouble();
        } catch (Exception ignored) {}
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
                if (obj.has("creativeCollision")) config.creativeCollision = obj.get("creativeCollision").getAsBoolean();
                if (obj.has("blockDisplayHitboxes")) config.blockDisplayHitboxes = obj.get("blockDisplayHitboxes").getAsBoolean();
                if (obj.has("itemDisplayHitboxes")) config.itemDisplayHitboxes = obj.get("itemDisplayHitboxes").getAsBoolean();
                if (obj.has("accurateHitboxes")) config.accurateHitboxes = obj.get("accurateHitboxes").getAsBoolean();
                if (obj.has("originIndependentLighting")) config.originIndependentLighting = obj.get("originIndependentLighting").getAsBoolean();

                if (obj.has("physicsKeepMomentum")) config.physicsKeepMomentum = obj.get("physicsKeepMomentum").getAsBoolean();
                config.physicsContactMargin = readDouble(obj, "physicsContactMargin", config.physicsContactMargin, 0.0, 0.5);
                config.physicsStandTolerance = readDouble(obj, "physicsStandTolerance", config.physicsStandTolerance, 0.0, 0.5);
                config.physicsMaxSpeed = readDouble(obj, "physicsMaxSpeed", config.physicsMaxSpeed, 0.1, 20.0);
                config.physicsMaxPush = readDouble(obj, "physicsMaxPush", config.physicsMaxPush, 0.05, 2.0);

                config.blockModelResolution = readInt(obj, "blockModelResolution", config.blockModelResolution, 4, 64);
                config.itemTextureResolution = readInt(obj, "itemTextureResolution", config.itemTextureResolution, 0, 128);
                config.rotatedSlicesPerBlock = readInt(obj, "rotatedSlicesPerBlock", config.rotatedSlicesPerBlock, 2, 64);
                config.maxRotatedSlices = readInt(obj, "maxRotatedSlices", config.maxRotatedSlices, 1, 128);
                config.tiltedResolutionPerBlock = readInt(obj, "tiltedResolutionPerBlock", config.tiltedResolutionPerBlock, 2, 32);
                config.maxTiltedResolution = readInt(obj, "maxTiltedResolution", config.maxTiltedResolution, 1, 64);
            } catch (Exception ignored) {}
        }
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