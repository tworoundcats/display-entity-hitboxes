package net.tworoundcats.displayblockhitboxes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CollisionSyncPayload(int entityId, boolean noCollision) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CollisionSyncPayload> ID = new CustomPacketPayload.Type<>(DisplayBlockHitboxes.SYNC_COLLISION);

    public static final StreamCodec<RegistryFriendlyByteBuf, CollisionSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CollisionSyncPayload::entityId,
            ByteBufCodecs.BOOL, CollisionSyncPayload::noCollision,
            CollisionSyncPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}