package dev.livelyeyes.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.livelyeyes.Config;
import dev.livelyeyes.LivelyEyesMod;
import dev.livelyeyes.logic.ExpressionController;
import dev.livelyeyes.logic.FaceState;
import dev.livelyeyes.logic.GazeMath;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Keeps one expression controller per visible player and picks the face texture when the player is drawn. */
public final class Expressions {
    public static final KeyMapping WINK = new KeyMapping("key.lively_eyes.wink", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA, "key.categories.lively_eyes");

    private static final long WINK_MS = 450;
    /** Players farther than this from the camera keep their skin as is (the face is a few pixels anyway). */
    private static final double ACTIVE_RANGE = 48;
    private static final double LOOK_RANGE = 8;
    private static final float LOW_HEALTH = 6;

    private static final Map<UUID, ExpressionController> CONTROLLERS = new HashMap<>();
    private static final Map<UUID, Integer> TARGETS = new HashMap<>();
    private static long winkUntil;
    private static boolean broken;
    private static int ticks;

    private Expressions() {
    }

    /** Called from the player renderer mixin on the render thread. Never throws: on error it turns itself off. */
    public static ResourceLocation textureFor(AbstractClientPlayer player, ResourceLocation original) {
        if (broken || !Config.ENABLED.get() || original == null) {
            return original;
        }
        try {
            Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            if (player.position().distanceToSqr(camera) > ACTIVE_RANGE * ACTIVE_RANGE) {
                return original;
            }
            long now = Util.getMillis();
            UUID id = player.getUUID();
            ExpressionController c = CONTROLLERS.computeIfAbsent(id,
                    u -> new ExpressionController(u.getMostSignificantBits() ^ u.getLeastSignificantBits(), now));
            boolean lowHealth = player.getHealth() <= LOW_HEALTH && !player.isCreative() && !player.isSpectator();
            ExpressionController.Inputs in = new ExpressionController.Inputs(
                    player.isSleeping(),
                    player.isDeadOrDying(),
                    player.hurtTime,
                    lowHealth,
                    TARGETS.getOrDefault(id, ExpressionController.NO_TARGET),
                    player == Minecraft.getInstance().player && now < winkUntil);
            FaceState face = c.update(now, in, Config.settings());
            return face.isNeutral() ? original : SkinFaces.variant(original, face);
        } catch (RuntimeException e) {
            broken = true;
            LivelyEyesMod.LOGGER.error("Expressions turned off after an error; the normal skin is drawn from now on", e);
            return original;
        }
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (WINK.consumeClick()) {
            winkUntil = Util.getMillis() + WINK_MS;
        }
        if (mc.level == null || broken) {
            return;
        }
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Set<UUID> present = new HashSet<>();
        TARGETS.clear();
        if (Config.ENABLED.get() && Config.GAZE.get()) {
            for (Player p : mc.level.players()) {
                present.add(p.getUUID());
                if (p.position().distanceToSqr(camera) <= ACTIVE_RANGE * ACTIVE_RANGE) {
                    int gaze = lookTarget(mc, p, camera);
                    if (gaze != ExpressionController.NO_TARGET) {
                        TARGETS.put(p.getUUID(), gaze);
                    }
                }
            }
        }
        if (++ticks % 200 == 0) {
            if (present.isEmpty()) {
                mc.level.players().forEach(p -> present.add(p.getUUID()));
            }
            CONTROLLERS.keySet().retainAll(present);
        }
    }

    /** Gaze toward the nearest living thing in front of the head; you, when you look at your own face in F5. */
    private static int lookTarget(Minecraft mc, Player p, Vec3 camera) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYHeadRot();
        if (p == mc.player && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            int g = GazeMath.gaze(yaw, eye.x, eye.z, camera.x, camera.z);
            if (g != ExpressionController.NO_TARGET) {
                return g;
            }
        }
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        int bestGaze = ExpressionController.NO_TARGET;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(LOOK_RANGE),
                e -> e != p && e.isAlive() && !e.isInvisible())) {
            Vec3 target = e.getEyePosition();
            double d = target.distanceToSqr(eye);
            if (d >= best || d > LOOK_RANGE * LOOK_RANGE) {
                continue;
            }
            int g = GazeMath.gaze(yaw, eye.x, eye.z, target.x, target.z);
            if (g != ExpressionController.NO_TARGET) {
                nearest = e;
                best = d;
                bestGaze = g;
            }
        }
        return nearest == null ? ExpressionController.NO_TARGET : bestGaze;
    }

    public static void clear() {
        CONTROLLERS.clear();
        TARGETS.clear();
    }
}
