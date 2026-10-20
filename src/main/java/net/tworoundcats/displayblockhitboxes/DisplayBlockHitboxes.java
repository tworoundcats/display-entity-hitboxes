package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

public class DisplayBlockHitboxes implements ModInitializer {
    public static final Identifier SYNC_COLLISION = new Identifier("displayblockhitboxes", "sync_collision");
    private static final java.util.Map<java.util.UUID, Long> LAST_TOGGLE_SERVER = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        LithiumCheck.run();
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> DisplayHitboxes.track(entity, world));
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));

        EntityTrackingEvents.START_TRACKING.register((entity, player) -> {
            if (entity instanceof DisplayEntity) {
                boolean noCollision = entity.getCommandTags().contains("deh_no_collision");
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(entity.getId());
                buf.writeBoolean(noCollision);
                ServerPlayNetworking.send(player, SYNC_COLLISION, buf);
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.isCreative() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof DisplayEntity) || !DisplayHitboxes.isTargetable(entity)) return ActionResult.PASS;

            if (world.isClient) {
                return ActionResult.PASS;
            }

            long now = world.getTime();
            Long prev = LAST_TOGGLE_SERVER.get(entity.getUuid());
            if (prev == null || now - prev > 3) {
                LAST_TOGGLE_SERVER.put(entity.getUuid(), now);

                boolean wasDisabled = entity.getCommandTags().contains("deh_no_collision");
                if (wasDisabled) entity.removeScoreboardTag("deh_no_collision");
                else entity.addCommandTag("deh_no_collision");

                boolean newState = !wasDisabled;
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeInt(entity.getId());
                buf.writeBoolean(newState);

                for (ServerPlayerEntity trackingPlayer : PlayerLookup.tracking(entity)) {
                    ServerPlayNetworking.send(trackingPlayer, SYNC_COLLISION, buf);
                }
                if (player instanceof ServerPlayerEntity sp && !PlayerLookup.tracking(entity).contains(sp)) {
                    ServerPlayNetworking.send(sp, SYNC_COLLISION, buf);
                }

                player.sendMessage(Text.literal("Collision: " + (wasDisabled ? "§aEnabled" : "§cDisabled")), true);
            }

            return ActionResult.SUCCESS;
        });
    }
}