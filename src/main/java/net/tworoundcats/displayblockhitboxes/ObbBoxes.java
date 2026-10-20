package net.tworoundcats.displayblockhitboxes;

import net.minecraft.util.math.Box;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class ObbBoxes {
    private static final double EPS = 1.0e-4;
    private static final double MIN_SIZE = 0.01;

    private static final int[][] FACES = {
            {0, 2, 6, 4}, {1, 3, 7, 5},
            {0, 1, 5, 4}, {2, 3, 7, 6},
            {0, 1, 3, 2}, {4, 5, 7, 6}
    };

    private ObbBoxes() {}

    public static void decompose(List<Box> locals, Matrix4f m, List<Box> out) {
        if (locals == null || locals.isEmpty()) return;

        boolean isAligned = isAxisAligned(m);
        if (isAligned || Math.abs(m.determinant()) < 1.0e-9) {
            List<Box> temp = new ArrayList<>();
            for (Box local : locals) {
                temp.add(transformBox(local, m));
            }
            out.addAll(cull(temp));
            return;
        }

        boolean yawOnly = Math.abs(m.m10()) < EPS && Math.abs(m.m12()) < EPS && Math.abs(m.m01()) < EPS && Math.abs(m.m21()) < EPS;
        boolean pitchOnly = Math.abs(m.m01()) < EPS && Math.abs(m.m02()) < EPS && Math.abs(m.m10()) < EPS && Math.abs(m.m20()) < EPS;
        boolean rollOnly = Math.abs(m.m02()) < EPS && Math.abs(m.m12()) < EPS && Math.abs(m.m20()) < EPS && Math.abs(m.m21()) < EPS;

        List<List<double[]>> allFaces = new ArrayList<>();
        double[] min = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        double[] max = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};

        for (Box local : locals) {
            double[][] c = new double[8][];
            for (int i = 0; i < 8; i++) {
                Vector4f v = new Vector4f(
                        (float) ((i & 1) == 0 ? local.minX : local.maxX),
                        (float) ((i & 2) == 0 ? local.minY : local.maxY),
                        (float) ((i & 4) == 0 ? local.minZ : local.maxZ),
                        1.0f);
                m.transform(v);
                c[i] = new double[]{v.x(), v.y(), v.z()};
                for (int k = 0; k < 3; k++) {
                    min[k] = Math.min(min[k], c[i][k]);
                    max[k] = Math.max(max[k], c[i][k]);
                }
            }
            for (int[] f : FACES) {
                List<double[]> poly = new ArrayList<>(4);
                for (int i : f) poly.add(c[i]);
                allFaces.add(poly);
            }
        }

        List<Box> boxes;
        if (yawOnly) {
            List<Box> a = cull(sliceAll(allFaces, min, max, 0, 2, true, true));
            List<Box> b = cull(sliceAll(allFaces, min, max, 2, 0, true, true));
            boxes = a.size() <= b.size() ? a : b;
        } else if (pitchOnly) {
            boxes = cull(sliceAll(allFaces, min, max, 2, 0, true, true));
        } else if (rollOnly) {
            boxes = cull(sliceAll(allFaces, min, max, 0, 2, true, true));
        } else {
            List<Box> a = cull(sliceAll(allFaces, min, max, 0, 2, false, false));
            List<Box> b = cull(sliceAll(allFaces, min, max, 2, 0, false, false));
            boxes = a.size() <= b.size() ? a : b;
        }

        if (boxes == null || boxes.isEmpty()) {
            out.add(pad(new Box(min[0], min[1], min[2], max[0], max[1], max[2])));
            return;
        }

        for (Box box : boxes) out.add(pad(box));
    }

    private static List<Box> sliceAll(List<List<double[]>> faces, double[] min, double[] max, int pAxis, int sAxis, boolean oneD, boolean isFine) {
        double minP = min[pAxis], maxP = max[pAxis];
        double minS = min[sAxis], maxS = max[sAxis];

        double extP = maxP - minP;
        double extS = maxS - minS;

        int cap = isFine ? DisplayHitboxConfig.get().maxRotatedSlices : DisplayHitboxConfig.get().maxTiltedResolution;
        double res = isFine ? (1.0 / Math.max(1, DisplayHitboxConfig.get().rotatedSlicesPerBlock))
                : (1.0 / Math.max(1, DisplayHitboxConfig.get().tiltedResolutionPerBlock));

        if (DisplayHitboxConfig.get().dynamicMaxSlices) {
            double scaleFactor = oneD ? extP : Math.max(extP, extS);
            cap = Math.min(isFine ? 1024 : 128, (int) Math.ceil(cap * Math.max(1.0, scaleFactor)));
        }

        int nP = clamp((int) Math.ceil(extP / res), 1, cap);
        int nS = oneD ? 1 : clamp((int) Math.ceil(extS / res), 1, cap);

        List<Box> result = new ArrayList<>();
        for (int i = 0; i < nP; i++) {
            double p0 = minP + extP * i / nP;
            double p1 = minP + extP * (i + 1) / nP;
            List<List<double[]>> slab = clipAll(faces, pAxis, p0, p1);

            Box run = null;
            for (int j = 0; j < nS; j++) {
                double s0 = minS + extS * j / nS;
                double s1 = minS + extS * (j + 1) / nS;

                Box cell = oneD ? bounds(slab) : bounds(clipAll(slab, sAxis, s0, s1));

                if (cell != null && run != null
                        && Math.abs(cell.minY - run.minY) < EPS && Math.abs(cell.maxY - run.maxY) < EPS) {
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

    private static List<Box> cull(List<Box> boxes) {
        if (boxes.size() <= 1) return boxes;
        Box[] arr = boxes.toArray(new Box[0]);
        int size = arr.length;

        boolean changed = true;
        while (changed) {
            changed = false;

            int newSize = 0;
            for (int i = 0; i < size; i++) {
                if (arr[i] != null) {
                    arr[newSize++] = arr[i];
                }
            }
            size = newSize;

            Arrays.sort(arr, 0, size, Comparator.comparingDouble(b -> b.minX));

            for (int i = 0; i < size; i++) {
                Box b1 = arr[i];
                if (b1 == null) continue;

                for (int j = i + 1; j < size; j++) {
                    Box b2 = arr[j];
                    if (b2 == null) continue;

                    if (b2.minX > b1.maxX + 0.016) {
                        break;
                    }

                    Box merged = tryMerge(b1, b2);
                    if (merged != null) {
                        b1 = merged;
                        arr[i] = b1;
                        arr[j] = null;
                        changed = true;
                    }
                }
            }
        }

        List<Box> result = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (arr[i] != null) result.add(arr[i]);
        }
        return result;
    }

    private static Box tryMerge(Box b1, Box b2) {
        if (b1.minX >= b2.minX - 0.015 && b1.maxX <= b2.maxX + 0.015 &&
                b1.minY >= b2.minY - 0.015 && b1.maxY <= b2.maxY + 0.015 &&
                b1.minZ >= b2.minZ - 0.015 && b1.maxZ <= b2.maxZ + 0.015) {
            return b2;
        }

        if (b2.minX >= b1.minX - 0.015 && b2.maxX <= b1.maxX + 0.015 &&
                b2.minY >= b1.minY - 0.015 && b2.maxY <= b1.maxY + 0.015 &&
                b2.minZ >= b1.minZ - 0.015 && b2.maxZ <= b1.maxZ + 0.015) {
            return b1;
        }

        if (Math.abs(b1.minX - b2.minX) < 0.015 && Math.abs(b1.maxX - b2.maxX) < 0.015 &&
                Math.abs(b1.minY - b2.minY) < 0.015 && Math.abs(b1.maxY - b2.maxY) < 0.015 &&
                Math.abs(b1.minZ - b2.minZ) < 0.015 && Math.abs(b1.maxZ - b2.maxZ) < 0.015) {
            return b1.union(b2);
        }

        double eps = 1.0e-3;
        boolean xMatch = Math.abs(b1.minX - b2.minX) < eps && Math.abs(b1.maxX - b2.maxX) < eps;
        boolean yMatch = Math.abs(b1.minY - b2.minY) < eps && Math.abs(b1.maxY - b2.maxY) < eps;
        boolean zMatch = Math.abs(b1.minZ - b2.minZ) < eps && Math.abs(b1.maxZ - b2.maxZ) < eps;

        if ((yMatch && zMatch && b1.maxX >= b2.minX - eps && b1.minX <= b2.maxX + eps) ||
                (xMatch && zMatch && b1.maxY >= b2.minY - eps && b1.minY <= b2.maxY + eps) ||
                (xMatch && yMatch && b1.maxZ >= b2.minZ - eps && b1.minZ <= b2.maxZ + eps)) {
            return b1.union(b2);
        }

        return null;
    }

    private static Box transformBox(Box local, Matrix4f m) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector4f v = new Vector4f(
                    (float) ((i & 1) == 0 ? local.minX : local.maxX),
                    (float) ((i & 2) == 0 ? local.minY : local.maxY),
                    (float) ((i & 4) == 0 ? local.minZ : local.maxZ),
                    1.0f);
            m.transform(v);
            minX = Math.min(minX, v.x()); maxX = Math.max(maxX, v.x());
            minY = Math.min(minY, v.y()); maxY = Math.max(maxY, v.y());
            minZ = Math.min(minZ, v.z()); maxZ = Math.max(maxZ, v.z());
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isAxisAligned(Matrix4f m) {
        return nonZero(m.m00(), m.m01(), m.m02()) <= 1
                && nonZero(m.m10(), m.m11(), m.m12()) <= 1
                && nonZero(m.m20(), m.m21(), m.m22()) <= 1;
    }

    private static int nonZero(float a, float b, float c) {
        return (Math.abs(a) > EPS ? 1 : 0) + (Math.abs(b) > EPS ? 1 : 0) + (Math.abs(c) > EPS ? 1 : 0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Box pad(Box b) {
        double px = Math.max(0.0, (MIN_SIZE - (b.maxX - b.minX)) / 2.0);
        double py = Math.max(0.0, (MIN_SIZE - (b.maxY - b.minY)) / 2.0);
        double pz = Math.max(0.0, (MIN_SIZE - (b.maxZ - b.minZ)) / 2.0);
        return (px > 0 || py > 0 || pz > 0) ? b.expand(px, py, pz) : b;
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