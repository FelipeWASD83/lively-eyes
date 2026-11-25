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
        double angle = angle(yawDeg, x, z, tx, tz);
        if (Double.isNaN(angle) || Math.abs(angle) > FIELD_OF_VIEW_DEG) {
            return ExpressionController.NO_TARGET;
        }
        if (Math.abs(angle) < CENTER_DEG) {
            return 0;
        }
        // the character's right is the texture's left
        return angle > 0 ? -1 : 1;
    }

    /**
     * Signed angle in degrees between where the head points and the point: 0 straight ahead, positive to the
     * character's right, +-180 behind. NaN when the point is on top of the head.
     */
    public static double angle(double yawDeg, double x, double z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) {
            return Double.NaN;
        }
        double yaw = Math.toRadians(yawDeg);
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        // the character's own right side: forward x up
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double forward = (dx * fx + dz * fz) / len;
        double side = (dx * rx + dz * rz) / len;
        return Math.toDegrees(Math.atan2(side, forward));
    }

    /** Two heads within this many degrees of pointing at each other count as face to face. */
    public static final double FACE_TO_FACE_DEG = 40;

    public static boolean faceToFace(double yawA, double ax, double az, double yawB, double bx, double bz) {
        double a = angle(yawA, ax, az, bx, bz), b = angle(yawB, bx, bz, ax, az);
        return !Double.isNaN(a) && Math.abs(a) <= FACE_TO_FACE_DEG && Math.abs(b) <= FACE_TO_FACE_DEG;
    }
}
