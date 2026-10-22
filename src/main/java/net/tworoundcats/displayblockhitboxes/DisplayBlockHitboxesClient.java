package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.tworoundcats.displayblockhitboxes.mixin.DisplayEntityAccessor;
import net.tworoundcats.displayblockhitboxes.mixin.ItemDisplayEntityAccessor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Optional;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DisplayBlockHitboxesClient implements ClientModInitializer {

    private static final Map<Integer, Boolean> PENDING_COLLISION_STATES = new ConcurrentHashMap<>();

    private static final int OUTLINE_COLOR = 0x66000000;
    private static final float LINE_WIDTH = 2.5f;

    private static volatile Display pickedDisplay;
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
            if (m != null && client.player != null) client.player.sendSystemMessage(Component.literal("§e" + m));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PENDING_COLLISION_STATES.clear();
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            DisplayHitboxes.track(entity, world);
            if (entity instanceof Display) {
                Boolean pending = PENDING_COLLISION_STATES.remove(entity.getId());
                if (pending != null) {
                    if (pending) entity.addTag("deh_no_collision");
                    else entity.removeTag("deh_no_collision");
                }
            }
        });

        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));

        ClientPlayNetworking.registerGlobalReceiver(CollisionSyncPayload.ID, (payload, context) -> {
            int entityId = payload.entityId();
            boolean noCollision = payload.noCollision();
            Minecraft client = context.client();
            client.execute(() -> {
                if (client.level != null) {
                    Entity entity = client.level.getEntity(entityId);
                    if (entity != null) {
                        if (noCollision) entity.addTag("deh_no_collision");
                        else entity.removeTag("deh_no_collision");
                    } else {
                        PENDING_COLLISION_STATES.put(entityId, noCollision);
                    }
                } else {
                    PENDING_COLLISION_STATES.put(entityId, noCollision);
                }
            });
        });

    }

    public static void renderOutline(SubmitNodeCollector consumers) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack matrices = new PoseStack();
        Vec3 cam = mc.gameRenderer.mainCamera().position();

        if (hitboxesVisible()) {
            if (slicesDrawn) return;
            slicesDrawn = true;
            for (Display other : DisplayHitboxes.displays(mc.level)) {
                if (other.distanceToSqr(cam) > 32 * 32) continue;
                if (!DisplayHitboxes.isTargetable(other)) continue;
                drawCollisionSlices(other, matrices, consumers, cam);
            }
            return;
        }

        if (mc.gameMode != null) {
            GameType mode = mc.gameMode.getPlayerMode();
            if (mode == GameType.SPECTATOR || mode == GameType.ADVENTURE) return;
        }

        Display display = pickedDisplay;
        if (display != null) {
            if (outlineDrawn) return;
            outlineDrawn = true;
        }

        if (display == null || display.isRemoved() || !DisplayHitboxes.isTargetable(display)) return;

        if (DisplayHitboxConfig.get().accurateHitboxes) {
            DisplayHitboxes.ShapeData data = DisplayHitboxes.getAccurateShape(display, true);
            if (data != null && !data.shape().isEmpty()) {
                matrices.pushPose();
                matrices.translate(display.getX() - cam.x, display.getY() - cam.y, display.getZ() - cam.z);

                Vector3f t = new Vector3f(display.getEntityData().get(DisplayEntityAccessor.getTranslation()));
                Vector3f s = new Vector3f(display.getEntityData().get(DisplayEntityAccessor.getScale()));
                Quaternionf lr = new Quaternionf(display.getEntityData().get(DisplayEntityAccessor.getLeftRotation()));
                Quaternionf rr = new Quaternionf(display.getEntityData().get(DisplayEntityAccessor.getRightRotation()));
                Quaternionf orient = new Quaternionf().rotationYXZ(-DisplayHitboxes.DEG * display.getYRot(), DisplayHitboxes.DEG * display.getXRot(), 0.0f);

                matrices.mulPose(orient);
                matrices.translate(t.x(), t.y(), t.z());
                matrices.mulPose(lr);
                matrices.scale(s.x(), s.y(), s.z());
                matrices.mulPose(rr);

                if (display instanceof Display.ItemDisplay itemDisplay) {
                    ItemStack stack = null;
                    net.minecraft.world.entity.SlotAccess ref = itemDisplay.getSlot(0);
                    if (ref != null) stack = ref.get();

                    if (stack != null && !stack.isEmpty()) {
                        int mode = itemDisplay.getEntityData().get(ItemDisplayEntityAccessor.getItemDisplay());
                        ItemModelBounds.applyInternalTransform(matrices, display.level(), stack, mode);
                    }
                }

                consumers.submitShapeOutline(matrices, data.shape(), RenderTypes.lines(), OUTLINE_COLOR, LINE_WIDTH, false);
                matrices.popPose();
                return;
            }
        }

        AABB world = DisplayHitboxes.computeBox(display, true);
        if (world == null) world = display.getBoundingBox();

        AABB box = world.move(-cam.x, -cam.y, -cam.z);
        consumers.submitShapeOutline(matrices, net.minecraft.world.phys.shapes.Shapes.create(box), RenderTypes.lines(), OUTLINE_COLOR, LINE_WIDTH, false);
    }

    private static void drawCollisionSlices(Display display, PoseStack matrices, SubmitNodeCollector consumers, Vec3 cam) {
        double dx = display.getX() - cam.x;
        double dy = display.getY() - cam.y;
        double dz = display.getZ() - cam.z;

        // simple mode collides with one box, show exactly that box
        if (!DisplayHitboxConfig.get().accurateHitboxes) {
            AABB box = display.getBoundingBox().move(-cam.x, -cam.y, -cam.z);
            consumers.submitShapeOutline(matrices, net.minecraft.world.phys.shapes.Shapes.create(box), RenderTypes.lines(), 0xFFFFFFFF, LINE_WIDTH, false);
            return;
        }

        java.util.List<AABB> boxes = DisplayHitboxes.getCollisionBoxes(display);
        if (boxes != null && !boxes.isEmpty()) {
            for (AABB local : boxes) {
                AABB box = local.move(dx, dy, dz);
                consumers.submitShapeOutline(matrices, net.minecraft.world.phys.shapes.Shapes.create(box), RenderTypes.lines(), 0xFFFFFFFF, LINE_WIDTH, false);
            }
        } else {
            AABB world = DisplayHitboxes.computeBox(display, true);
            if (world == null) world = display.getBoundingBox();
            AABB box = world.move(-cam.x, -cam.y, -cam.z);
            consumers.submitShapeOutline(matrices, net.minecraft.world.phys.shapes.Shapes.create(box), RenderTypes.lines(), 0xFFFFFFFF, LINE_WIDTH, false);
        }
    }

    public static void pick(float tickDelta) {
        pickedDisplay = null;
        outlineDrawn = false;
        slicesDrawn = false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        double entityReach = mc.player.entityInteractionRange();
        Vec3 eye = cam.getEyePosition(tickDelta);
        Vec3 end = eye.add(cam.getViewVector(tickDelta).scale(entityReach));

        HitResult cur = mc.hitResult;
        double bound = entityReach * entityReach;
        if (cur != null && cur.getType() != HitResult.Type.MISS) {
            bound = Math.min(bound, cur.getLocation().distanceToSqr(eye));
        }

        Display best = null;
        Vec3 bestPos = null;

        AABB searchBox = new AABB(
                eye.x - entityReach, eye.y - entityReach, eye.z - entityReach,
                eye.x + entityReach, eye.y + entityReach, eye.z + entityReach
        );

        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;
        Iterable<Display> candidates = accurate
                ? DisplayHitboxes.displays(mc.level)
                : mc.level.getEntitiesOfClass(Display.class, searchBox, DisplayHitboxes::isTargetable);

        for (Display display : candidates) {
            if (!DisplayHitboxes.isTargetable(display) || display.isSpectator()) continue;
            if (accurate && !DisplayHitboxes.mayIntersect(display, searchBox)) continue;

            Optional<Vec3> hit = DisplayHitboxes.raycast(display, eye, end);
            if (hit.isEmpty()) continue;

            double d = hit.get().distanceToSqr(eye);
            if (d <= bound + 1.0e-3) {
                bound = d;
                best = display;
                bestPos = hit.get();
            }
        }

        if (best != null) {
            mc.hitResult = new EntityHitResult(best, bestPos);
            pickedDisplay = best;
        }
    }
}