package dev.livelyeyes.logic;

/** Which way the irises should slide so a head looks at a point. Minecraft yaw: 0 faces +Z, 90 faces -X. */
public final class GazeMath {
    /** Targets farther than this to the side of the nose are out of view. */
    public static final double FIELD_OF_VIEW_DEG = 75;
    /** Targets this close to straight ahead keep the irises centered. */
    public static final double CENTER_DEG = 12;

    private GazeMath() {
    }

    /**
     * Texture-space gaze (-1, 0, +1) to look from (x, z) with head yaw {@code yawDeg} at (tx, tz), or
     * {@link ExpressionController#NO_TARGET} when the point is behind or beside the head.
     */
    public static int gaze(double yawDeg, double x, double z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            return ExpressionController.NO_TARGET;
        }
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        // the character's own right side: forward x up
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double forward = (dx * fx + dz * fz) / len;
        double side = (dx * rx + dz * rz) / len;
        double angle = Math.toDegrees(Math.atan2(side, forward));
        if (Math.abs(angle) > FIELD_OF_VIEW_DEG) {
            return ExpressionController.NO_TARGET;
        }
        if (Math.abs(angle) < CENTER_DEG) {
            return 0;
        }
        // the character's right is the texture's left
        return angle > 0 ? -1 : 1;
    }
}
