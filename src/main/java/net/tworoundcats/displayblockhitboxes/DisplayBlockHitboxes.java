package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;

public class DisplayBlockHitboxes implements ModInitializer {
    public static final Identifier SYNC_COLLISION = Identifier.fromNamespaceAndPath("displayblockhitboxes", "sync_collision");
    private static final java.util.Map<java.util.UUID, Long> LAST_TOGGLE_SERVER = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LithiumCheck.run();
        PayloadTypeRegistry.clientboundPlay().register(CollisionSyncPayload.ID, CollisionSyncPayload.CODEC);
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> DisplayHitboxes.track(entity, world));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof Display) {
                boolean noCollision = entity.entityTags().contains("deh_no_collision");
                ServerPlayNetworking.send(player, new CollisionSyncPayload(entity.getId(), noCollision));
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.isCreative() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(entity instanceof Display) || !DisplayHitboxes.isTargetable(entity)) return InteractionResult.PASS;

            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }

            long now = world.getGameTime();
            Long prev = LAST_TOGGLE_SERVER.get(entity.getUUID());
            if (prev == null || now - prev > 3) {
                LAST_TOGGLE_SERVER.put(entity.getUUID(), now);

                boolean wasDisabled = entity.entityTags().contains("deh_no_collision");
                if (wasDisabled) entity.removeTag("deh_no_collision");
                else entity.addTag("deh_no_collision");

                boolean newState = !wasDisabled;
                CollisionSyncPayload payload = new CollisionSyncPayload(entity.getId(), newState);

                for (ServerPlayer trackingPlayer : PlayerLookup.tracking(entity)) {
                    ServerPlayNetworking.send(trackingPlayer, payload);
                }
                if (player instanceof ServerPlayer sp && !PlayerLookup.tracking(entity).contains(sp)) {
                    ServerPlayNetworking.send(sp, payload);
                }

                if (player instanceof ServerPlayer self) {
                    self.sendSystemMessage(Component.literal("Collision: " + (wasDisabled ? "§aEnabled" : "§cDisabled")), true);
                }
            }

            return InteractionResult.SUCCESS;
        });
    }
}