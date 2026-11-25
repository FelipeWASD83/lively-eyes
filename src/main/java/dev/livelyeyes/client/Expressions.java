package dev.livelyeyes.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.livelyeyes.Config;
import dev.livelyeyes.LivelyEyesMod;
import dev.livelyeyes.logic.ExpressionController;
import dev.livelyeyes.logic.FaceState;
import dev.livelyeyes.logic.GazeMath;
import dev.livelyeyes.logic.Mood;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Keeps one expression state per visible player, reads what the game shows about them every tick (fighting, close
 * to someone, falling, eating...) and picks the face texture when the player is drawn.
 */
public final class Expressions {
    public static final String KEY_CATEGORY = "key.categories.lively_eyes";
    public static final KeyMapping WINK = new KeyMapping("key.lively_eyes.wink", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_COMMA, KEY_CATEGORY);
    public static final KeyMapping MENU = new KeyMapping("key.lively_eyes.menu", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PERIOD, KEY_CATEGORY);

    private static final long WINK_MS = 450;
    /** How long an expression picked in the menu stays on. */
    public static final long MANUAL_MS = 3000;
    /** Players farther than this from the camera keep their skin as is (the face is a few pixels anyway). */
    private static final double ACTIVE_RANGE = 48;
    private static final double LOOK_RANGE = 8;
    private static final double FIGHT_RANGE = 4.5;
    private static final double FACE_TO_FACE_RANGE = 1.8;
    private static final float LOW_HEALTH = 6;
    private static final float CRITICAL_HEALTH = 4;
    /** Falling faster than this many blocks per tick is a scare (a jump peaks at about 0.4). */
    private static final double FALL_SPEED = 0.7;
    // reactions linger a little after their trigger, so they read as expressions and not flicker
    private static final long ANGER_HOLD_MS = 1500, BLUSH_HOLD_MS = 800, STARTLE_HOLD_MS = 700;

    /** What is known about one player between ticks. */
    private static final class Tracked {
        final ExpressionController controller;
        int target = ExpressionController.NO_TARGET;
        long angryUntil, blushUntil, startledUntil;

        Tracked(UUID id, long now) {
            controller = new ExpressionController(id.getMostSignificantBits() ^ id.getLeastSignificantBits(), now);
        }
    }

    private static final Map<UUID, Tracked> PLAYERS = new HashMap<>();
    private static long winkUntil;
    private static Mood manual;
    private static long manualUntil;
    private static boolean broken;
    private static int ticks;

    private Expressions() {
    }

    /** Shows an expression on your own face for a few seconds (from the menu). */
    public static void pick(Mood mood) {
        manual = mood;
        manualUntil = Util.getMillis() + MANUAL_MS;
    }

    /** Called from the player renderer mixin on the render thread. Never throws: on error it turns itself off. */
    public static ResourceLocation textureFor(AbstractClientPlayer player, ResourceLocation original) {
        if (broken || !Config.ENABLED.get() || original == null) {
            return original;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
            if (player.position().distanceToSqr(camera) > ACTIVE_RANGE * ACTIVE_RANGE) {
                return original;
            }
            long now = Util.getMillis();
            Tracked t = PLAYERS.computeIfAbsent(player.getUUID(), id -> new Tracked(id, now));
            boolean you = player == mc.player;
            boolean survival = !player.isCreative() && !player.isSpectator();

            ExpressionController.Inputs in = ExpressionController.Inputs.idle();
            in.sleeping = player.isSleeping();
            in.dead = player.isDeadOrDying();
            in.hurtTime = player.hurtTime;
            in.lowHealth = survival && player.getHealth() <= LOW_HEALTH;
            in.critical = survival && player.getHealth() <= CRITICAL_HEALTH;
            in.targetGaze = t.target;
            in.winking = you && now < winkUntil;
            in.fighting = now < t.angryUntil;
            in.faceToFace = now < t.blushUntil;
            in.startled = now < t.startledUntil;
            in.eating = isEating(player);
            in.freezing = player.getTicksFrozen() > 0;
            in.manual = you && now < manualUntil ? manual : null;

            FaceState face = t.controller.update(now, in, Config.settings());
            return face.isNeutral() ? original : SkinFaces.variant(original, face);
        } catch (RuntimeException e) {
            broken = true;
            LivelyEyesMod.LOGGER.error("Expressions turned off after an error; the normal skin is drawn from now on", e);
            return original;
        }
    }

    private static boolean isEating(Player p) {
        if (!p.isUsingItem()) {
            return false;
        }
        UseAnim anim = p.getUseItem().getUseAnimation();
        return anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (WINK.consumeClick()) {
            winkUntil = Util.getMillis() + WINK_MS;
        }
        while (MENU.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new ExpressionMenuScreen());
            }
        }
        if (mc.level == null || broken || !Config.ENABLED.get()) {
            return;
        }
        long now = Util.getMillis();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        Set<UUID> present = new HashSet<>();
        for (Player p : mc.level.players()) {
            present.add(p.getUUID());
            if (p.position().distanceToSqr(camera) > ACTIVE_RANGE * ACTIVE_RANGE) {
                continue;
            }
            Tracked t = PLAYERS.computeIfAbsent(p.getUUID(), id -> new Tracked(id, now));
            observe(mc, p, t, camera, now);
        }
        if (++ticks % 200 == 0) {
            PLAYERS.keySet().retainAll(present);
        }
    }

    /** Reads the triggers that need to look around the player: gaze target, fight, someone close, a fall. */
    private static void observe(Minecraft mc, Player p, Tracked t, Vec3 camera, long now) {
        Vec3 eye = p.getEyePosition();
        float yaw = p.getYHeadRot();
        t.target = ExpressionController.NO_TARGET;
        // in front view your own eyes look at you, whoever else is around
        boolean lookingAtCamera = p == mc.player && mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT;
        if (lookingAtCamera) {
            t.target = GazeMath.gaze(yaw, eye.x, eye.z, camera.x, camera.z);
        }
        double best = Double.MAX_VALUE;
        boolean foeInFront = false;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(LOOK_RANGE),
                e -> e != p && e.isAlive() && !e.isInvisible())) {
            Vec3 target = e.getEyePosition();
            double d = target.distanceToSqr(eye);
            if (d > LOOK_RANGE * LOOK_RANGE) {
                continue;
            }
            int g = GazeMath.gaze(yaw, eye.x, eye.z, target.x, target.z);
            if (g == ExpressionController.NO_TARGET) {
                continue;
            }
            if (!lookingAtCamera && d < best) {
                best = d;
                t.target = g;
            }
            foeInFront |= d <= FIGHT_RANGE * FIGHT_RANGE;
            if (e instanceof Player other && d <= FACE_TO_FACE_RANGE * FACE_TO_FACE_RANGE
                    && GazeMath.faceToFace(yaw, eye.x, eye.z, other.getYHeadRot(), target.x, target.z)) {
                t.blushUntil = now + BLUSH_HOLD_MS;
            }
        }
        if (!Config.GAZE.get()) {
            t.target = ExpressionController.NO_TARGET;
        }
        if (p.swinging && foeInFront) {
            t.angryUntil = now + ANGER_HOLD_MS;
        }
        boolean flying = p.isFallFlying() || p.getAbilities().flying || p.isInWater();
        if (p.isOnFire() || !flying && p.getY() - p.yo < -FALL_SPEED) {
            t.startledUntil = now + STARTLE_HOLD_MS;
        }
    }

    public static void clear() {
        PLAYERS.clear();
        manual = null;
        manualUntil = 0;
    }
}
