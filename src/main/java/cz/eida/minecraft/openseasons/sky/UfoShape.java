/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.sky;

import java.util.ArrayList;
import java.util.List;

/**
 * Look of the craft: a black flat triangle with straight edges and lights inside it.
 * Craft coordinates are in blocks: right, up, forward (in the direction of flight).
 *
 * <p>The hull consists of thin plates. Each plate is a parallelogram (an affine image of a block cube), so
 * the edges are straight lines, not pixel stairs. Triangle A (tip), B, C is covered exactly by two parallelograms
 * B–M–middle of AC–middle of AB and C–M–middle of AB–middle of AC (M is the middle of BC); only the upper
 * triangle of half the size remains uncovered and the procedure repeats on it. After {@link #HULL_LEVELS} levels
 * only a tip of size {@code SIZE / 2^HULL_LEVELS} is missing.
 */
public final class UfoShape {

    /** length (from the tail to the tip) and span of the triangle */
    public static final double SIZE = 14.0;

    /** thickness of the hull plates */
    public static final double THICKNESS = 0.25;

    /** subdivision levels of the hull; there are twice as many plates */
    public static final int HULL_LEVELS = 5;

    /** hull vertices: tip in front, left and right wing at the back */
    static final double[] NOSE = {0, 0, SIZE / 2};
    static final double[] LEFT_TIP = {-SIZE / 2, 0, -SIZE / 2};
    static final double[] RIGHT_TIP = {SIZE / 2, 0, -SIZE / 2};

    /** scale of the ● glyph for the lights (about 0.7 block) */
    public static final float LIGHT_SCALE = 4f;

    /** how far from the centroid towards a corner the lights are (1 = exactly in the corner) */
    static final double LIGHT_INSET = 0.72;

    /** one hull plate: corner and two edges in craft coordinates (right, up, forward) */
    public record Piece(double[] origin, double[] u, double[] v) {
    }

    /** one light: position in craft coordinates and whether it blinks red */
    public record Light(double right, double up, double forward, boolean blinking) {
    }

    /** white lights near the corners and a red blinking one in the centroid, all in the black area just below the hull */
    public static final List<Light> LIGHTS;

    /** offset of the other craft of the formation relative to the leader: right, up, forward */
    public static final List<double[]> FORMATION = List.of(
            new double[]{0, 0, 0},
            new double[]{-22, 1.5, -18},
            new double[]{22, 1.5, -18});

    static {
        double[] g = centroid();
        double below = -(THICKNESS / 2 + 0.15);
        List<Light> lights = new ArrayList<>();
        for (double[] corner : List.of(NOSE, LEFT_TIP, RIGHT_TIP)) {
            lights.add(new Light(g[0] + LIGHT_INSET * (corner[0] - g[0]), below,
                    g[2] + LIGHT_INSET * (corner[2] - g[2]), false));
        }
        lights.add(new Light(g[0], below, g[2], true));
        LIGHTS = List.copyOf(lights);
    }

    private UfoShape() {
    }

    /** centroid of the hull */
    static double[] centroid() {
        return new double[]{(NOSE[0] + LEFT_TIP[0] + RIGHT_TIP[0]) / 3, 0, (NOSE[2] + LEFT_TIP[2] + RIGHT_TIP[2]) / 3};
    }

    /** hull plates; together they cover the triangle except for a tiny tip and never stick out of it */
    public static List<Piece> hullPieces() {
        List<Piece> pieces = new ArrayList<>();
        double[] a = NOSE;
        double[] b = LEFT_TIP;
        double[] c = RIGHT_TIP;
        for (int level = 0; level < HULL_LEVELS; level++) {
            double[] m = mid(b, c);
            double[] ab = mid(a, b);
            double[] ac = mid(a, c);
            pieces.add(new Piece(b, sub(m, b), sub(ab, b)));
            pieces.add(new Piece(c, sub(m, c), sub(ac, c)));
            b = ab;
            c = ac;
        }
        return List.copyOf(pieces);
    }

    /**
     * Block transformation for a plate: a 4×4 matrix by columns (for {@code Matrix4f#set(float[])})
     * in display coordinates, where x = −right (yaw 0 faces south, right is west), y = up,
     * z = forward. The block cube 0–1 is mapped onto a plate of thickness {@link #THICKNESS} centred
     * at the hull height. The determinant is positive so that the faces do not turn inside out.
     */
    public static float[] displayMatrix(Piece piece) {
        double[] u = toDisplay(piece.u());
        double[] v = toDisplay(piece.v());
        double[] o = toDisplay(piece.origin());
        // det(u, (0,T,0), v) = T * (u.x * v.z - u.z * v.x)
        if (u[0] * v[2] - u[2] * v[0] < 0) {
            double[] t = u;
            u = v;
            v = t;
        }
        return new float[]{
                (float) u[0], (float) u[1], (float) u[2], 0f,
                0f, (float) THICKNESS, 0f, 0f,
                (float) v[0], (float) v[1], (float) v[2], 0f,
                (float) o[0], (float) (o[1] - THICKNESS / 2), (float) o[2], 1f};
    }

    /**
     * Plate as a display transformation: translation, left rotation around the vertical axis, scale and right rotation
     * around the vertical axis ({@code Transformation} of Bukkit). The horizontal part of the affine matrix of the plate is
     * decomposed into rotations and scales (closed-form 2×2 SVD), so that the whole plate can turn with the heading
     * and the client interpolates it smoothly. The angles are for {@code Quaternionf#rotationY}.
     *
     * @param translation corner of the plate in display coordinates (x = −right, y = up, z = forward)
     */
    public record PieceTransform(double[] translation, double leftAngle, double[] scale, double rightAngle) {
    }

    public static PieceTransform decompose(Piece piece) {
        float[] m = displayMatrix(piece);
        // horizontal part: input x → column 0, input z → column 2; output (x, z)
        double a = m[0], c = m[2];
        double b = m[8], d = m[10];
        double e = (a + d) / 2, f = (a - d) / 2, g = (c + b) / 2, h = (c - b) / 2;
        double q = Math.hypot(e, h), r = Math.hypot(f, g);
        double a1 = Math.atan2(g, f), a2 = Math.atan2(h, e);
        double theta = (a2 - a1) / 2, phi = (a2 + a1) / 2;
        // a 2D rotation by α in the (x, z) plane is rotationY(−α)
        return new PieceTransform(new double[]{m[12], m[13], m[14]}, -phi, new double[]{q + r, THICKNESS, q - r},
                -theta);
    }

    /** the red light is on for 8 ticks out of every 30 */
    public static boolean redLightOn(int tick) {
        return Math.floorMod(tick, 30) < 8;
    }

    private static double[] toDisplay(double[] local) {
        return new double[]{-local[0], local[1], local[2]};
    }

    private static double[] mid(double[] p, double[] q) {
        return new double[]{(p[0] + q[0]) / 2, (p[1] + q[1]) / 2, (p[2] + q[2]) / 2};
    }

    private static double[] sub(double[] p, double[] q) {
        return new double[]{p[0] - q[0], p[1] - q[1], p[2] - q[2]};
    }
}
