package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.tworoundcats.displayblockhitboxes.mixin.DisplayEntityAccessor;
import net.tworoundcats.displayblockhitboxes.mixin.ItemDisplayEntityAccessor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayBlockHitboxesClient implements ClientModInitializer {

    private static final Map<Integer, Boolean> PENDING_COLLISION_STATES = new ConcurrentHashMap<>();

    private static final int OUTLINE_COLOR = 0x66000000;

    private static volatile DisplayEntity pickedDisplay;
    private static volatile boolean outlineDrawn;
    private static volatile boolean slicesDrawn;
    private static volatile long hitboxesSeenAt = System.nanoTime() - 1_000_000_000L;

    public static void noteVanillaHitboxes() {
        hitboxesSeenAt = System.nanoTime();
    }

    private static boolean hitboxesVisible() {
        return System.nanoTime() - hitboxesSeenAt < 250_000_000L;
    }

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(DisplayPhysics::tickClient);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String m = LithiumCheck.message();
            if (m != null && client.player != null) client.player.sendMessage(Text.literal("§e" + m), false);
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PENDING_COLLISION_STATES.clear();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            DisplayHitboxes.track(entity, world);
            if (entity instanceof DisplayEntity) {
                Boolean pending = PENDING_COLLISION_STATES.remove(entity.getId());
                if (pending != null) {
                    if (pending) entity.addCommandTag("deh_no_collision");
                    else entity.removeCommandTag("deh_no_collision");
                }
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));

        ClientPlayNetworking.registerGlobalReceiver(CollisionSyncPayload.ID, (payload, context) -> {
            int entityId = payload.entityId();
            boolean noCollision = payload.noCollision();
            MinecraftClient client = context.client();
            client.execute(() -> {
                if (client.world != null) {
                    Entity entity = client.world.getEntityById(entityId);
                    if (entity != null) {
                        if (noCollision) entity.addCommandTag("deh_no_collision");
                        else entity.removeCommandTag("deh_no_collision");
                    } else {
                        PENDING_COLLISION_STATES.put(entityId, noCollision);
                    }
                } else {
                    PENDING_COLLISION_STATES.put(entityId, noCollision);
                }
            });
        });

    }

    public static void renderOutline(String hook) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        MatrixStack matrices = new MatrixStack();
        Vec3d cam = mc.gameRenderer.getCamera().getPos();

        if (hitboxesVisible()) {
            if (slicesDrawn) return;
            slicesDrawn = true;
            for (DisplayEntity other : DisplayHitboxes.displays(mc.world)) {
                if (other.squaredDistanceTo(cam) > 32 * 32) continue;
                if (!DisplayHitboxes.isTargetable(other)) continue;
                drawCollisionSlices(other, matrices, consumers, cam);
            }
            return;
        }

        if (mc.interactionManager != null) {
            GameMode mode = mc.interactionManager.getCurrentGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.ADVENTURE) return;
        }

        DisplayEntity display = pickedDisplay;
        if (display != null) {
            if (outlineDrawn) return;
            outlineDrawn = true;
        }

        if (display == null || display.isRemoved() || !DisplayHitboxes.isTargetable(display)) return;

        if (DisplayHitboxConfig.get().accurateHitboxes) {
            DisplayHitboxes.ShapeData data = DisplayHitboxes.getAccurateShape(display, true);
            if (data != null && !data.shape().isEmpty()) {
                matrices.push();
                matrices.translate(display.getX() - cam.x, display.getY() - cam.y, display.getZ() - cam.z);

                Vector3f t = display.getDataTracker().get(DisplayEntityAccessor.getTranslation());
                Vector3f s = display.getDataTracker().get(DisplayEntityAccessor.getScale());
                Quaternionf lr = display.getDataTracker().get(DisplayEntityAccessor.getLeftRotation());
                Quaternionf rr = display.getDataTracker().get(DisplayEntityAccessor.getRightRotation());
                Quaternionf orient = new Quaternionf().rotationYXZ(-DisplayHitboxes.DEG * display.getYaw(), DisplayHitboxes.DEG * display.getPitch(), 0.0f);

                matrices.multiply(orient);
                matrices.translate(t.x(), t.y(), t.z());
                matrices.multiply(lr);
                matrices.scale(s.x(), s.y(), s.z());
                matrices.multiply(rr);

                if (display instanceof DisplayEntity.ItemDisplayEntity itemDisplay) {
                    ItemStack stack = null;
                    net.minecraft.inventory.StackReference ref = itemDisplay.getStackReference(0);
                    if (ref != null) stack = ref.get();

                    if (stack != null && !stack.isEmpty()) {
                        int mode = itemDisplay.getDataTracker().get(ItemDisplayEntityAccessor.getItemDisplay());
                        ItemModelBounds.applyInternalTransform(matrices, display.getEntityWorld(), stack, mode);
                    }
                }

                VertexRendering.drawOutline(matrices, consumers.getBuffer(RenderLayer.getLines()), data.shape(), 0.0, 0.0, 0.0, OUTLINE_COLOR);
                consumers.draw(RenderLayer.getLines());
                matrices.pop();
                return;
            }
        }

        Box world = DisplayHitboxes.computeBox(display, true);
        if (world == null) world = display.getBoundingBox();

        Box box = world.offset(-cam.x, -cam.y, -cam.z);
        VertexRendering.drawOutline(matrices, consumers.getBuffer(RenderLayer.getLines()), net.minecraft.util.shape.VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, OUTLINE_COLOR);
        consumers.draw(RenderLayer.getLines());
    }

    private static void drawCollisionSlices(DisplayEntity display, MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Vec3d cam) {
        double dx = display.getX() - cam.x;
        double dy = display.getY() - cam.y;
        double dz = display.getZ() - cam.z;

        java.util.List<Box> boxes = DisplayHitboxes.getCollisionBoxes(display);
        if (boxes != null && !boxes.isEmpty()) {
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
            for (Box local : boxes) {
                Box box = local.offset(dx, dy, dz);
                VertexRendering.drawOutline(matrices, buffer, net.minecraft.util.shape.VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, 0xFFFFFFFF);
            }
        } else {
            Box world = DisplayHitboxes.computeBox(display, true);
            if (world == null) world = display.getBoundingBox();
            Box box = world.offset(-cam.x, -cam.y, -cam.z);
            VertexRendering.drawOutline(matrices, consumers.getBuffer(RenderLayer.getLines()), net.minecraft.util.shape.VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, 0xFFFFFFFF);
        }
        consumers.draw(RenderLayer.getLines());
    }

    public static void pick(float tickDelta) {
        pickedDisplay = null;
        outlineDrawn = false;
        slicesDrawn = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        double entityReach = mc.player.getEntityInteractionRange();
        Vec3d eye = cam.getCameraPosVec(tickDelta);
        Vec3d end = eye.add(cam.getRotationVec(tickDelta).multiply(entityReach));

        HitResult cur = mc.crosshairTarget;
        double bound = entityReach * entityReach;
        if (cur != null && cur.getType() != HitResult.Type.MISS) {
            bound = Math.min(bound, cur.getPos().squaredDistanceTo(eye));
        }

        DisplayEntity best = null;
        Vec3d bestPos = null;

        Box searchBox = new Box(
                eye.x - entityReach, eye.y - entityReach, eye.z - entityReach,
                eye.x + entityReach, eye.y + entityReach, eye.z + entityReach
        );

        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;
        Iterable<DisplayEntity> candidates = accurate
                ? DisplayHitboxes.displays(mc.world)
                : mc.world.getEntitiesByClass(DisplayEntity.class, searchBox, DisplayHitboxes::isTargetable);

        for (DisplayEntity display : candidates) {
            if (!DisplayHitboxes.isTargetable(display) || display.isSpectator()) continue;
            if (accurate && !DisplayHitboxes.mayIntersect(display, searchBox)) continue;

            Optional<Vec3d> hit = DisplayHitboxes.raycast(display, eye, end);
            if (hit.isEmpty()) continue;

            double d = hit.get().squaredDistanceTo(eye);
            if (d <= bound + 1.0e-3) {
                bound = d;
                best = display;
                bestPos = hit.get();
            }
        }

        if (best != null) {
            mc.crosshairTarget = new EntityHitResult(best, bestPos);
            pickedDisplay = best;
        }
    }
}