/*
 * OpenSeasons - public copy
 * Author: EidaCz
 *
 * This file is part of the public copy of the OpenSeasons plugin, which is part of the
 * non-public MC-215 project. Licensed under the GNU General Public License v3.0 (see LICENSE).
 */
package cz.eida.minecraft.openseasons.sky;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UfoShapeTest {

    private static final double EPS = 1e-9;

    /** barycentric coordinates of a point (right, forward) in the hull; all ≥ 0 = inside */
    private static double[] barycentric(double r, double f) {
        double[] a = UfoShape.NOSE, b = UfoShape.LEFT_TIP, c = UfoShape.RIGHT_TIP;
        double det = (b[2] - c[2]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[2] - c[2]);
        double l1 = ((b[2] - c[2]) * (r - c[0]) + (c[0] - b[0]) * (f - c[2])) / det;
        double l2 = ((c[2] - a[2]) * (r - c[0]) + (a[0] - c[0]) * (f - c[2])) / det;
        return new double[]{l1, l2, 1 - l1 - l2};
    }

    private static boolean insideTriangle(double r, double f) {
        for (double l : barycentric(r, f)) if (l < -EPS) return false;
        return true;
    }

    private static boolean insidePiece(UfoShape.Piece p, double r, double f) {
        double ur = p.u()[0], uf = p.u()[2], vr = p.v()[0], vf = p.v()[2];
        double det = ur * vf - uf * vr;
        double dr = r - p.origin()[0], df = f - p.origin()[2];
        double s = (dr * vf - df * vr) / det;
        double t = (ur * df - uf * dr) / det;
        return s >= -EPS && s <= 1 + EPS && t >= -EPS && t <= 1 + EPS;
    }

    @Test
    void piecesStayInsideTheTriangle() {
        List<UfoShape.Piece> pieces = UfoShape.hullPieces();
        assertEquals(2 * UfoShape.HULL_LEVELS, pieces.size());
        for (UfoShape.Piece p : pieces) {
            double[] o = p.origin();
            for (double[] corner : List.of(o, new double[]{o[0] + p.u()[0], 0, o[2] + p.u()[2]},
                    new double[]{o[0] + p.v()[0], 0, o[2] + p.v()[2]},
                    new double[]{o[0] + p.u()[0] + p.v()[0], 0, o[2] + p.u()[2] + p.v()[2]})) {
                assertTrue(insideTriangle(corner[0], corner[2]), "plate corner outside the hull");
            }
        }
    }

    @Test
    void piecesCoverTheTriangleExceptTheTinyTip() {
        List<UfoShape.Piece> pieces = UfoShape.hullPieces();
        double tip = UfoShape.SIZE / Math.pow(2, UfoShape.HULL_LEVELS);
        int checked = 0;
        for (double r = -UfoShape.SIZE / 2; r <= UfoShape.SIZE / 2; r += 0.25) {
            for (double f = -UfoShape.SIZE / 2; f <= UfoShape.SIZE / 2; f += 0.25) {
                if (!insideTriangle(r, f) || f > UfoShape.SIZE / 2 - tip) continue;
                final double rr = r, ff = f;
                assertTrue(pieces.stream().anyMatch(p -> insidePiece(p, rr, ff)),
                        "uncovered point " + r + ", " + f);
                checked++;
            }
        }
        assertTrue(checked > 1000);
    }

    @Test
    void displayMatrixMapsBlockOntoPieceWithPositiveDeterminant() {
        for (UfoShape.Piece p : UfoShape.hullPieces()) {
            Matrix4f m = new Matrix4f().set(UfoShape.displayMatrix(p));
            assertTrue(m.determinant() > 0, "inverted faces");
            // the cube corner (0,0,0) lies in the corner of the plate, the bottom below the hull level
            Vector3f o = m.transformPosition(new Vector3f(0, 0, 0));
            Vector3f far = m.transformPosition(new Vector3f(1, 1, 1));
            assertEquals(-UfoShape.THICKNESS / 2, o.y, 1e-6);
            assertEquals(UfoShape.THICKNESS / 2, far.y, 1e-6);
            // the display has x = −right: the opposite corner of the plate
            double rOpp = p.origin()[0] + p.u()[0] + p.v()[0];
            double fOpp = p.origin()[2] + p.u()[2] + p.v()[2];
            assertEquals(-rOpp, far.x, 1e-5);
            assertEquals(fOpp, far.z, 1e-5);
        }
    }

    @Test
    void decompositionRebuildsTheSameMatrix() {
        for (UfoShape.Piece p : UfoShape.hullPieces()) {
            Matrix4f expected = new Matrix4f().set(UfoShape.displayMatrix(p));
            UfoShape.PieceTransform t = UfoShape.decompose(p);
            assertTrue(t.scale()[0] > 0 && t.scale()[1] > 0 && t.scale()[2] > 0, "negative scale");
            Matrix4f rebuilt = new Matrix4f()
                    .translation((float) t.translation()[0], (float) t.translation()[1], (float) t.translation()[2])
                    .rotate(new org.joml.Quaternionf().rotationY((float) t.leftAngle()))
                    .scale((float) t.scale()[0], (float) t.scale()[1], (float) t.scale()[2])
                    .rotate(new org.joml.Quaternionf().rotationY((float) t.rightAngle()));
            for (Vector3f corner : List.of(new Vector3f(0, 0, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1),
                    new Vector3f(1, 1, 1), new Vector3f(0.3f, 0.5f, 0.8f))) {
                Vector3f e = expected.transformPosition(new Vector3f(corner));
                Vector3f r = rebuilt.transformPosition(new Vector3f(corner));
                assertEquals(e.x, r.x, 1e-4);
                assertEquals(e.y, r.y, 1e-4);
                assertEquals(e.z, r.z, 1e-4);
            }
        }
    }

    @Test
    void lightsSitInsideTheBlackHullJustBelowIt() {
        for (UfoShape.Light l : UfoShape.LIGHTS) {
            for (double b : barycentric(l.right(), l.forward())) {
                assertTrue(b > 0.05, "light too close to the edge or outside the hull: " + l);
            }
            assertTrue(l.up() < -UfoShape.THICKNESS / 2, "light is not below the hull");
        }
        assertEquals(1, UfoShape.LIGHTS.stream().filter(UfoShape.Light::blinking).count());
    }
}
