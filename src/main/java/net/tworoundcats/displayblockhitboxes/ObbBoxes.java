package net.tworoundcats.displayblockhitboxes;

import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public final class ObbBoxes {
    private static final double EPS = 1.0e-4;
    private static final double FINE = 1.0 / DisplayHitboxConfig.get().rotatedSlicesPerBlock;
    private static final double COARSE = 1.0 / DisplayHitboxConfig.get().tiltedResolutionPerBlock;
    private static final double MIN_SIZE = 0.01;

    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 5, 7, 6}
    };

    private ObbBoxes() {}

    public static void decompose(Box local, Matrix4f m, List<Box> out) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) {
            Vector4f v = new Vector4f(
                    (float) ((i & 1) == 0 ? local.minX : local.maxX),
                    (float) ((i & 2) == 0 ? local.minY : local.maxY),
                    (float) ((i & 4) == 0 ? local.minZ : local.maxZ),
                    1.0f);
            m.transform(v);
            c[i] = new double[]{v.x(), v.y(), v.z()};
        }

        List<Box> boxes = null;
        if (!isAxisAligned(m) && Math.abs(m.determinant()) > 1.0e-9) {
            boolean yawOnly = Math.abs(m.m10()) < EPS && Math.abs(m.m12()) < EPS
                    && Math.abs(m.m01()) < EPS && Math.abs(m.m21()) < EPS;
            List<Box> a = slice(c, 0, 2, yawOnly);
            List<Box> b = slice(c, 2, 0, yawOnly);
            boxes = volume(a) <= volume(b) ? a : b;
        }

        if (boxes == null || boxes.isEmpty()) {
            double[] lo = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
            double[] hi = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
            for (double[] v : c) {
                for (int k = 0; k < 3; k++) {
                    lo[k] = Math.min(lo[k], v[k]);
                    hi[k] = Math.max(hi[k], v[k]);
                }
            }
            out.add(pad(new Box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])));
            return;
        }

        for (Box b : boxes) out.add(pad(b));
    }

    private static boolean isAxisAligned(Matrix4f m) {
        return nonZero(m.m00(), m.m01(), m.m02()) <= 1
                && nonZero(m.m10(), m.m11(), m.m12()) <= 1
                && nonZero(m.m20(), m.m21(), m.m22()) <= 1;
    }

    private static int nonZero(float a, float b, float c) {
        return (Math.abs(a) > EPS ? 1 : 0) + (Math.abs(b) > EPS ? 1 : 0) + (Math.abs(c) > EPS ? 1 : 0);
    }

    private static Box pad(Box b) {
        double px = Math.max(0.0, (MIN_SIZE - (b.maxX - b.minX)) / 2.0);
        double py = Math.max(0.0, (MIN_SIZE - (b.maxY - b.minY)) / 2.0);
        double pz = Math.max(0.0, (MIN_SIZE - (b.maxZ - b.minZ)) / 2.0);
        return (px > 0 || py > 0 || pz > 0) ? b.expand(px, py, pz) : b;
    }

    private static double volume(List<Box> boxes) {
        double v = 0.0;
        for (Box b : boxes) v += (b.maxX - b.minX) * (b.maxY - b.minY) * (b.maxZ - b.minZ);
        return v;
    }

    private static List<Box> slice(double[][] c, int pAxis, int sAxis, boolean yawOnly) {
        double minP = Double.MAX_VALUE, maxP = -Double.MAX_VALUE;
        double minS = Double.MAX_VALUE, maxS = -Double.MAX_VALUE;
        for (double[] v : c) {
            minP = Math.min(minP, v[pAxis]);
            maxP = Math.max(maxP, v[pAxis]);
            minS = Math.min(minS, v[sAxis]);
            maxS = Math.max(maxS, v[sAxis]);
        }
        double extP = maxP - minP, extS = maxS - minS;

        int nP, nS;
        if (yawOnly) {
            nP = clamp((int) Math.ceil(extP / FINE), 1, DisplayHitboxConfig.get().maxRotatedSlices);
            nS = 1;
        } else {
            nP = clamp((int) Math.ceil(extP / COARSE), 1, DisplayHitboxConfig.get().maxTiltedResolution);
            nS = clamp((int) Math.ceil(extS / COARSE), 1, DisplayHitboxConfig.get().maxTiltedResolution);
        }

        List<List<double[]>> faces = new ArrayList<>(6);
        for (int[] f : FACES) {
            List<double[]> poly = new ArrayList<>(4);
            for (int i : f) poly.add(c[i]);
            faces.add(poly);
        }

        List<Box> result = new ArrayList<>();
        for (int i = 0; i < nP; i++) {
            double p0 = i == 0 ? minP - 1.0e-7 : minP + extP * i / nP;
            double p1 = i == nP - 1 ? maxP + 1.0e-7 : minP + extP * (i + 1) / nP;
            List<List<double[]>> slab = clipAll(faces, pAxis, p0, p1);

            Box run = null;
            for (int j = 0; j < nS; j++) {
                double s0 = j == 0 ? minS - 1.0e-7 : minS + extS * j / nS;
                double s1 = j == nS - 1 ? maxS + 1.0e-7 : minS + extS * (j + 1) / nS;
                Box cell = bounds(clipAll(slab, sAxis, s0, s1));

                if (cell != null && run != null
                        && Math.abs(cell.minY - run.minY) < 1.0e-4 && Math.abs(cell.maxY - run.maxY) < 1.0e-4) {
                    run = new Box(
                            Math.min(run.minX, cell.minX), Math.min(run.minY, cell.minY), Math.min(run.minZ, cell.minZ),
                            Math.max(run.maxX, cell.maxX), Math.max(run.maxY, cell.maxY), Math.max(run.maxZ, cell.maxZ));
                } else {
                    if (run != null) result.add(run);
                    run = cell;
                }
            }
            if (run != null) result.add(run);
        }
        return result;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Box bounds(List<List<double[]>> polys) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        boolean any = false;
        for (List<double[]> poly : polys) {
            for (double[] v : poly) {
                any = true;
                minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]);
                minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
                minZ = Math.min(minZ, v[2]); maxZ = Math.max(maxZ, v[2]);
            }
        }
        // nothing inside, or it only touches the cell border
        if (!any || maxX - minX < 1.0e-6 || maxZ - minZ < 1.0e-6) return null;
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static List<List<double[]>> clipAll(List<List<double[]>> polys, int axis, double lo, double hi) {
        List<List<double[]>> out = new ArrayList<>(polys.size());
        for (List<double[]> p : polys) {
            out.add(p.isEmpty() ? p : clip(clip(p, axis, lo, true), axis, hi, false));
        }
        return out;
    }

    private static List<double[]> clip(List<double[]> poly, int axis, double bound, boolean keepGreater) {
        List<double[]> out = new ArrayList<>(poly.size() + 2);
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            double[] a = poly.get(i), b = poly.get((i + 1) % n);
            double da = keepGreater ? a[axis] - bound : bound - a[axis];
            double db = keepGreater ? b[axis] - bound : bound - b[axis];
            if (da >= 0) out.add(a);
            if ((da >= 0) != (db >= 0)) {
                double t = da / (da - db);
                out.add(new double[]{
                        a[0] + (b[0] - a[0]) * t,
                        a[1] + (b[1] - a[1]) * t,
                        a[2] + (b[2] - a[2]) * t});
            }
        }
        return out;
    }
}