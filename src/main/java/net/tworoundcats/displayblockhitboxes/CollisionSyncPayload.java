package net.tworoundcats.displayblockhitboxes;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record CollisionSyncPayload(int entityId, boolean noCollision) implements CustomPayload {
    public static final CustomPayload.Id<CollisionSyncPayload> ID = new CustomPayload.Id<>(DisplayBlockHitboxes.SYNC_COLLISION);

    public static final PacketCodec<RegistryByteBuf, CollisionSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CollisionSyncPayload::entityId,
            PacketCodecs.BOOL, CollisionSyncPayload::noCollision,
            CollisionSyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}