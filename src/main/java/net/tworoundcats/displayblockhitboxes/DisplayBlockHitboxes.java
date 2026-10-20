package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

public class DisplayBlockHitboxes implements ModInitializer {
    @Override
    public void onInitialize() {
        LithiumCheck.run();
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> DisplayHitboxes.track(entity, world));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));
    }
}