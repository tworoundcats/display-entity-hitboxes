package net.tworoundcats.displayblockhitboxes;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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

public class DisplayBlockHitboxesClient implements ClientModInitializer {
    private static final java.util.Map<java.util.UUID, Long> LAST_TOGGLE_CLIENT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_TOGGLE_SERVER = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(DisplayPhysics::tickClient);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String m = LithiumCheck.message();
            if (m != null && client.player != null) client.player.sendMessage(Text.literal("§e" + m), false);
        });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> DisplayHitboxes.track(entity, world));
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, world) -> DisplayHitboxes.untrack(entity, world));

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean canPick = false;
            if (mc.interactionManager != null) {
                GameMode mode = mc.interactionManager.getCurrentGameMode();
                canPick = (mode != GameMode.SPECTATOR && mode != GameMode.ADVENTURE);
            }

            boolean f3b = mc.getEntityRenderDispatcher().shouldRenderHitboxes();
            HitResult hit = mc.crosshairTarget;
            Entity targetEntity = (hit instanceof EntityHitResult entityHit) ? entityHit.getEntity() : null;

            if (!canPick) targetEntity = null;

            VertexConsumerProvider consumers = context.consumers();
            if (consumers == null) return;
            MatrixStack matrices = context.matrixStack();
            Vec3d cam = context.camera().getPos();

            if (!f3b && targetEntity instanceof DisplayEntity display && DisplayHitboxes.isTargetable(display)) {
                drawAccurateVisualOutline(display, matrices, consumers, cam);
            }

            if (f3b) {
                net.minecraft.client.render.Frustum frustum = context.frustum();
                for (DisplayEntity display : DisplayHitboxes.displays(mc.world)) {
                    if (display.squaredDistanceTo(cam) > 32 * 32) continue;
                    if (!DisplayHitboxes.isTargetable(display)) continue;

                    Box bounds = display.getBoundingBox();
                    if (frustum != null && !frustum.isVisible(bounds)) continue;

                    drawCollisionSlices(display, matrices, consumers, cam);
                }
            }
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!player.isCreative() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(entity instanceof DisplayEntity) || !DisplayHitboxes.isTargetable(entity)) return ActionResult.PASS;

            java.util.Map<java.util.UUID, Long> last = world.isClient ? LAST_TOGGLE_CLIENT : LAST_TOGGLE_SERVER;
            long now = world.getTime();
            Long prev = last.get(entity.getUuid());
            if (prev == null || now - prev > 3) {
                last.put(entity.getUuid(), now);

                boolean wasDisabled = entity.getCommandTags().contains("deh_no_collision");
                if (wasDisabled) entity.removeScoreboardTag("deh_no_collision");
                else entity.addCommandTag("deh_no_collision");

                if (world.isClient) {
                    player.sendMessage(Text.literal("Collision: " + (wasDisabled ? "§aEnabled" : "§cDisabled")), true);
                }
            }
            return world.isClient ? ActionResult.PASS : ActionResult.SUCCESS;
        });
    }

    private static void drawAccurateVisualOutline(DisplayEntity display, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
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
                    try {
                        net.minecraft.inventory.StackReference ref = itemDisplay.getStackReference(0);
                        if (ref != null) stack = ref.get();
                    } catch (Exception ignored) {}

                    if (stack != null && !stack.isEmpty()) {
                        int mode = itemDisplay.getDataTracker().get(ItemDisplayEntityAccessor.getItemDisplay());
                        ItemModelBounds.applyInternalTransform(matrices, display.getWorld(), stack, mode);
                    }
                }

                WorldRenderer.drawShapeOutline(matrices, consumers.getBuffer(RenderLayer.getLines()), data.shape(), 0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f, 0.4f, true);
                matrices.pop();
                return;
            }
        }

        Box world = DisplayHitboxes.computeBox(display, true);
        if (world == null) world = display.getBoundingBox();
        Box box = world.offset(-cam.x, -cam.y, -cam.z);
        WorldRenderer.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box, 0.0f, 0.0f, 0.0f, 0.4f);
    }

    private static void drawCollisionSlices(DisplayEntity display, MatrixStack matrices, VertexConsumerProvider consumers, Vec3d cam) {
        double dx = display.getX() - cam.x;
        double dy = display.getY() - cam.y;
        double dz = display.getZ() - cam.z;

        java.util.List<Box> boxes = DisplayHitboxes.getCollisionBoxes(display);
        if (boxes != null && !boxes.isEmpty()) {
            VertexConsumer buffer = consumers.getBuffer(RenderLayer.getLines());
            for (Box local : boxes) {
                Box box = local.offset(dx, dy, dz);
                WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        } else {
            Box world = DisplayHitboxes.computeBox(display, true);
            if (world == null) world = display.getBoundingBox();
            Box box = world.offset(-cam.x, -cam.y, -cam.z);
            WorldRenderer.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    public static void pick(float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;

        Entity cam = mc.getCameraEntity();
        if (cam == null) return;

        double entityReach = mc.interactionManager.hasExtendedReach() ? 6.0 : 3.0;
        Vec3d eye = cam.getCameraPosVec(tickDelta);
        Vec3d end = eye.add(cam.getRotationVec(tickDelta).multiply(entityReach));

        HitResult cur = mc.crosshairTarget;
        double bound = entityReach * entityReach;
        if (cur != null && cur.getType() != HitResult.Type.MISS) {
            bound = Math.min(bound, cur.getPos().squaredDistanceTo(eye));
        }

        Entity best = null;
        Vec3d bestPos = null;

        Box searchBox = new Box(
                eye.x - entityReach, eye.y - entityReach, eye.z - entityReach,
                eye.x + entityReach, eye.y + entityReach, eye.z + entityReach
        );

        boolean accurate = DisplayHitboxConfig.get().accurateHitboxes;
        // vanilla lookup misses displays whose box is far from their origin, so it uses its own list in accurate mode
        Iterable<DisplayEntity> candidates = accurate
                ? DisplayHitboxes.displays(mc.world)
                : mc.world.getEntitiesByClass(DisplayEntity.class, searchBox, DisplayHitboxes::isTargetable);

        for (DisplayEntity display : candidates) {
            if (!DisplayHitboxes.isTargetable(display) || display.isSpectator()) continue;
            if (accurate && !DisplayHitboxes.mayIntersect(display, searchBox)) continue;

            Optional<Vec3d> hit = DisplayHitboxes.raycast(display, eye, end);
            if (hit.isEmpty()) continue;

            double d = hit.get().squaredDistanceTo(eye);
            if (d < bound) {
                bound = d;
                best = display;
                bestPos = hit.get();
            }
        }

        if (best != null) {
            mc.crosshairTarget = new EntityHitResult(best, bestPos);
        }
    }
}