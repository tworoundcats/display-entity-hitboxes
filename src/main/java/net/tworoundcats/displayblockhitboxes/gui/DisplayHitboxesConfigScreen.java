package net.tworoundcats.displayblockhitboxes.gui;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import net.tworoundcats.displayblockhitboxes.BlockModelBounds;
import net.tworoundcats.displayblockhitboxes.ItemModelBounds;

public class DisplayHitboxesConfigScreen {

    private static class State {
        DisplayHitboxConfig.Preset selectedPreset;
    }

    public static Screen buildScreen(Screen parent) {
        DisplayHitboxConfig config = DisplayHitboxConfig.get();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("Display Entity Hitboxes"));

        State s = new State();

        DisplayHitboxConfig.Preset[] availablePresets = {
                DisplayHitboxConfig.Preset.LOW,
                DisplayHitboxConfig.Preset.DEFAULT,
                DisplayHitboxConfig.Preset.HIGH,
                DisplayHitboxConfig.Preset.ULTRA
        };

        DisplayHitboxConfig.Preset initialPreset = config.preset == DisplayHitboxConfig.Preset.CUSTOM
                ? DisplayHitboxConfig.Preset.DEFAULT : config.preset;
        s.selectedPreset = initialPreset;

        builder.setSavingRunnable(() -> {
            if (s.selectedPreset != initialPreset) {
                config.applyPreset(s.selectedPreset);
            } else {
                config.determinePreset();
            }
            config.save();

            BlockModelBounds.clearCache();
            ItemModelBounds.clearCache();
            DisplayHitboxes.clearCache();
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Accurate Hitboxes"), config.accurateHitboxes)
                .setDefaultValue(true)
                .setTooltip(Text.literal(config.accurateHitboxes_info))
                .setSaveConsumer(v -> config.accurateHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Block Display Hitboxes"), config.blockDisplayHitboxes)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Generate solid hitboxes and physics for Block Display entities."))
                .setSaveConsumer(v -> config.blockDisplayHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Item Display Hitboxes"), config.itemDisplayHitboxes)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Generate solid hitboxes and physics for Item Display entities."))
                .setSaveConsumer(v -> config.itemDisplayHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Origin Independent Lighting"), config.originIndependentLighting)
                .setDefaultValue(true)
                .setTooltip(Text.literal(config.originIndependentLighting_info))
                .setSaveConsumer(v -> config.originIndependentLighting = v)
                .build());

        ConfigCategory physics = builder.getOrCreateCategory(Text.literal("Physics"));

        physics.addEntry(entryBuilder.startBooleanToggle(Text.literal("Display Physics"), config.displayPhysics)
                .setDefaultValue(true)
                .setTooltip(Text.literal(config.displayPhysics_info))
                .setSaveConsumer(v -> config.displayPhysics = v)
                .build());

        physics.addEntry(entryBuilder.startBooleanToggle(Text.literal("Keep Momentum"), config.physicsKeepMomentum)
                .setDefaultValue(true)
                .setTooltip(Text.literal("Players keep their momentum when walking/falling off of moving displays."))
                .setSaveConsumer(v -> config.physicsKeepMomentum = v)
                .build());

        ConfigCategory accuracy = builder.getOrCreateCategory(Text.literal("Accuracy"));

        accuracy.addEntry(entryBuilder.startSelector(Text.literal("Quality Preset"), availablePresets, initialPreset)
                .setTooltip(Text.literal("Select a preset to apply it. Custom slider tweaks save as Custom automatically."))
                .setSaveConsumer(v -> s.selectedPreset = v)
                .build());

        accuracy.addEntry(entryBuilder.startIntSlider(Text.literal("Block Model Resolution"), config.blockModelResolution, 4, 128)
                .setDefaultValue(32)
                .setTooltip(Text.literal(config.blockModelResolution_info))
                .setSaveConsumer(v -> config.blockModelResolution = v)
                .build());

        accuracy.addEntry(entryBuilder.startIntSlider(Text.literal("Rotated Slices Per Block"), config.rotatedSlicesPerBlock, 2, 128)
                .setDefaultValue(16)
                .setTooltip(Text.literal(config.rotatedSlicesPerBlock_info))
                .setSaveConsumer(v -> config.rotatedSlicesPerBlock = v)
                .build());

        accuracy.addEntry(entryBuilder.startIntField(Text.literal("Max Rotated Slices"), config.maxRotatedSlices)
                .setDefaultValue(24)
                .setMin(1).setMax(2048)
                .setTooltip(Text.literal(config.maxRotatedSlices_info))
                .setSaveConsumer(v -> config.maxRotatedSlices = v)
                .build());

        accuracy.addEntry(entryBuilder.startIntSlider(Text.literal("Tilted Grid Resolution"), config.tiltedResolutionPerBlock, 2, 64)
                .setDefaultValue(8)
                .setTooltip(Text.literal(config.tiltedResolutionPerBlock_info))
                .setSaveConsumer(v -> config.tiltedResolutionPerBlock = v)
                .build());

        accuracy.addEntry(entryBuilder.startIntField(Text.literal("Max Tilted Resolution"), config.maxTiltedResolution)
                .setDefaultValue(16)
                .setMin(1).setMax(256)
                .setTooltip(Text.literal(config.maxTiltedResolution_info))
                .setSaveConsumer(v -> config.maxTiltedResolution = v)
                .build());

        accuracy.addEntry(entryBuilder.startBooleanToggle(Text.literal("Dynamic Max Slices"), config.dynamicMaxSlices)
                .setDefaultValue(false)
                .setTooltip(Text.literal(config.dynamicMaxSlices_info))
                .setSaveConsumer(v -> config.dynamicMaxSlices = v)
                .build());

        return builder.build();
    }
}