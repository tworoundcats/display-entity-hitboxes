package net.tworoundcats.displayblockhitboxes.gui;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.tworoundcats.displayblockhitboxes.BlockModelBounds;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxConfig;
import net.tworoundcats.displayblockhitboxes.DisplayHitboxes;
import net.tworoundcats.displayblockhitboxes.ItemModelBounds;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DisplayHitboxesConfigScreen {

    private static final DisplayHitboxConfig.Preset CUSTOM = DisplayHitboxConfig.Preset.CUSTOM;

    private record Values(int blockRes, int rotated, int maxRotated,
                          int tilted, int maxTilted, boolean dynamic) {

        static Values of(DisplayHitboxConfig c) {
            return new Values(c.blockModelResolution, c.rotatedSlicesPerBlock, c.maxRotatedSlices,
                    c.tiltedResolutionPerBlock, c.maxTiltedResolution, c.dynamicMaxSlices);
        }

        static Values of(DisplayHitboxConfig.Preset p) {
            DisplayHitboxConfig temp = new DisplayHitboxConfig();
            temp.applyPreset(p);
            return of(temp);
        }

        void applyTo(DisplayHitboxConfig c) {
            c.blockModelResolution = blockRes;
            c.rotatedSlicesPerBlock = rotated;
            c.maxRotatedSlices = maxRotated;
            c.tiltedResolutionPerBlock = tilted;
            c.maxTiltedResolution = maxTilted;
            c.dynamicMaxSlices = dynamic;
        }
    }

    private static class State {
        AbstractConfigListEntry<DisplayHitboxConfig.Preset> presetEntry;
        AbstractConfigListEntry<Integer> blockResEntry, rotatedEntry, maxRotatedEntry, tiltedEntry, maxTiltedEntry;
        AbstractConfigListEntry<Boolean> dynamicEntry;

        DisplayHitboxConfig.Preset lastShown;
        Values lastValues;

        DisplayHitboxConfig.Preset logical;

        boolean busy = false;
    }

    private static Values readValues(State s) {
        try {
            return new Values(
                    s.blockResEntry.getValue(),
                    s.rotatedEntry.getValue(),
                    s.maxRotatedEntry.getValue(),
                    s.tiltedEntry.getValue(),
                    s.maxTiltedEntry.getValue(),
                    s.dynamicEntry.getValue());
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeValues(State s, Values v) {
        setEntryValue(s.blockResEntry, v.blockRes());
        setEntryValue(s.rotatedEntry, v.rotated());
        setEntryValue(s.maxRotatedEntry, v.maxRotated());
        setEntryValue(s.tiltedEntry, v.tilted());
        setEntryValue(s.maxTiltedEntry, v.maxTilted());
        setEntryValue(s.dynamicEntry, v.dynamic());
    }

    private static boolean isAssignable(Class<?> param, Object value) {
        if (param.isPrimitive()) {
            if (param == int.class) return value instanceof Integer;
            if (param == boolean.class) return value instanceof Boolean;
            if (param == long.class) return value instanceof Long;
            if (param == double.class) return value instanceof Double;
            if (param == float.class) return value instanceof Float;
            return false;
        }
        return param.isInstance(value);
    }

    private static boolean setEntryValue(Object entry, Object value) {
        if (entry == null || value == null) return false;

        for (Method m : entry.getClass().getMethods()) {
            if (!m.getName().equals("setValue") || m.getParameterCount() != 1 || m.isBridge()) continue;
            if (!isAssignable(m.getParameterTypes()[0], value)) continue;
            try {
                m.invoke(entry, value);
                return true;
            } catch (Exception ignored) {
            }
        }
        return setViaFields(entry, value);
    }

    private static boolean setViaFields(Object entry, Object value) {
        try {
            AtomicInteger index = null;
            AtomicBoolean flag = null;
            List<List<?>> lists = new ArrayList<>();

            for (Class<?> c = entry.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Object o = f.get(entry);
                    if (o instanceof AtomicInteger a && index == null) index = a;
                    else if (o instanceof AtomicBoolean b && flag == null) flag = b;
                    else if (o instanceof List<?> l) lists.add(l);
                }
            }

            if (value instanceof Boolean b && flag != null) {
                flag.set(b);
                return true;
            }
            if (index != null) {
                for (List<?> l : lists) {
                    int i = l.indexOf(value);
                    if (i >= 0) {
                        index.set(i);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static DisplayHitboxConfig.Preset matchPreset(Values v) {
        DisplayHitboxConfig temp = new DisplayHitboxConfig();
        v.applyTo(temp);
        temp.determinePreset();
        return temp.preset;
    }

    private static DisplayHitboxConfig.Preset skipCustom(DisplayHitboxConfig.Preset from) {
        DisplayHitboxConfig.Preset[] all = DisplayHitboxConfig.Preset.values();
        int n = all.length;
        if (n < 2 || from == null) return CUSTOM;
        int custom = CUSTOM.ordinal();
        boolean forward = (from.ordinal() + 1) % n == custom;
        int idx = forward ? (custom + 1) % n : (custom - 1 + n) % n;
        return all[idx];
    }

    private static void sync(State s) {
        if (s.busy) return;
        s.busy = true;
        try {
            DisplayHitboxConfig.Preset shown = s.presetEntry.getValue();
            Values now = readValues(s);
            if (shown == null || now == null) return;

            if (shown != s.lastShown) {
                DisplayHitboxConfig.Preset target = shown == CUSTOM ? skipCustom(s.lastShown) : shown;

                if (target == CUSTOM) {
                    s.lastShown = shown;
                    s.lastValues = now;
                    s.logical = CUSTOM;
                    return;
                }
                setEntryValue(s.presetEntry, target);
                writeValues(s, Values.of(target));
                s.logical = target;

            } else if (!now.equals(s.lastValues)) {
                DisplayHitboxConfig.Preset match = matchPreset(now);
                if (match != shown) setEntryValue(s.presetEntry, match);
                s.logical = match;

            } else {
                return;
            }
            DisplayHitboxConfig.Preset after = s.presetEntry.getValue();
            Values afterValues = readValues(s);
            s.lastShown = after != null ? after : shown;
            s.lastValues = afterValues != null ? afterValues : now;

        } catch (Exception ignored) {
        } finally {
            s.busy = false;
        }
    }

    public static Screen buildScreen(Screen parent) {
        DisplayHitboxConfig config = DisplayHitboxConfig.get();
        DisplayHitboxConfig defaults = new DisplayHitboxConfig();
        config.determinePreset();

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("Display Entity Hitboxes"));

        State s = new State();
        DisplayHitboxConfig.Preset[] availablePresets = DisplayHitboxConfig.Preset.values();
        builder.setSavingRunnable(() -> {
            sync(s);
            if (s.logical != null && s.logical != CUSTOM) {
                Values.of(s.logical).applyTo(config);
                config.preset = s.logical;
            } else {
                config.determinePreset();
            }
            config.save();

            BlockModelBounds.clearCache();
            ItemModelBounds.clearCache();
            DisplayHitboxes.clearCache();
        });

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Accurate Hitboxes"), config.accurateHitboxes)
                .setDefaultValue(true)
                .setTooltip(Component.literal(config.accurateHitboxes_info))
                .setSaveConsumer(v -> config.accurateHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Block Display Hitboxes"), config.blockDisplayHitboxes)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Generate solid hitboxes and physics for Block Display entities."))
                .setSaveConsumer(v -> config.blockDisplayHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Item Display Hitboxes"), config.itemDisplayHitboxes)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Generate solid hitboxes and physics for Item Display entities."))
                .setSaveConsumer(v -> config.itemDisplayHitboxes = v)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Component.literal("Origin Independent Lighting"), config.originIndependentLighting)
                .setDefaultValue(true)
                .setTooltip(Component.literal(config.originIndependentLighting_info))
                .setSaveConsumer(v -> config.originIndependentLighting = v)
                .build());

        ConfigCategory physics = builder.getOrCreateCategory(Component.literal("Physics"));

        physics.addEntry(entryBuilder.startBooleanToggle(Component.literal("Display Physics"), config.displayPhysics)
                .setDefaultValue(true)
                .setTooltip(Component.literal(config.displayPhysics_info))
                .setSaveConsumer(v -> config.displayPhysics = v)
                .build());

        physics.addEntry(entryBuilder.startBooleanToggle(Component.literal("Keep Momentum"), config.physicsKeepMomentum)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Players keep their momentum when walking/falling off of moving displays."))
                .setSaveConsumer(v -> config.physicsKeepMomentum = v)
                .build());

        ConfigCategory accuracy = builder.getOrCreateCategory(Component.literal("Accuracy"));

        s.presetEntry = entryBuilder.startSelector(Component.literal("Quality Preset"), availablePresets, config.preset)
                .setDefaultValue(defaults.preset)
                .setTooltip(Component.literal("Select a preset to apply it. Custom slider tweaks switch this to Custom automatically."))
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.presetEntry);

        s.blockResEntry = entryBuilder.startIntSlider(Component.literal("Block Model Resolution"), config.blockModelResolution, 4, 128)
                .setDefaultValue(defaults.blockModelResolution)
                .setTooltip(Component.literal(config.blockModelResolution_info))
                .setSaveConsumer(v -> config.blockModelResolution = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.blockResEntry);

        s.rotatedEntry = entryBuilder.startIntSlider(Component.literal("Rotated Slices Per Block"), config.rotatedSlicesPerBlock, 2, 128)
                .setDefaultValue(defaults.rotatedSlicesPerBlock)
                .setTooltip(Component.literal(config.rotatedSlicesPerBlock_info))
                .setSaveConsumer(v -> config.rotatedSlicesPerBlock = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.rotatedEntry);

        s.maxRotatedEntry = entryBuilder.startIntField(Component.literal("Max Rotated Slices"), config.maxRotatedSlices)
                .setDefaultValue(defaults.maxRotatedSlices)
                .setMin(1).setMax(2048)
                .setTooltip(Component.literal(config.maxRotatedSlices_info))
                .setSaveConsumer(v -> config.maxRotatedSlices = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.maxRotatedEntry);

        s.tiltedEntry = entryBuilder.startIntSlider(Component.literal("Tilted Grid Resolution"), config.tiltedResolutionPerBlock, 2, 64)
                .setDefaultValue(defaults.tiltedResolutionPerBlock)
                .setTooltip(Component.literal(config.tiltedResolutionPerBlock_info))
                .setSaveConsumer(v -> config.tiltedResolutionPerBlock = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.tiltedEntry);

        s.maxTiltedEntry = entryBuilder.startIntField(Component.literal("Max Tilted Resolution"), config.maxTiltedResolution)
                .setDefaultValue(defaults.maxTiltedResolution)
                .setMin(1).setMax(256)
                .setTooltip(Component.literal(config.maxTiltedResolution_info))
                .setSaveConsumer(v -> config.maxTiltedResolution = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.maxTiltedEntry);

        s.dynamicEntry = entryBuilder.startBooleanToggle(Component.literal("Dynamic Max Slices"), config.dynamicMaxSlices)
                .setDefaultValue(defaults.dynamicMaxSlices)
                .setTooltip(Component.literal(config.dynamicMaxSlices_info))
                .setSaveConsumer(v -> config.dynamicMaxSlices = v)
                .setErrorSupplier(v -> {
                    sync(s);
                    return Optional.empty();
                })
                .build();
        accuracy.addEntry(s.dynamicEntry);

        s.lastShown = s.presetEntry.getValue();
        s.logical = s.lastShown;
        Values initial = readValues(s);
        s.lastValues = initial != null ? initial : Values.of(config);

        return builder.build();
    }
}