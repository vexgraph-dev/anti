package math;

import lang.FastMath;
import lang.Vec3;
import nio.ForeignMemory;

/**
 * Off-Heap Bounding Sphere representation (Center Vec3 + Radius float) stored in 16-byte native memory.
 * Designed for zero-GC allocation, raytracing, and bounding volume hierarchy (BVH) testing.
 */
public final class Sphere
{
    public static final long BYTES = 16L; // 12B Center Vec3 + 4B Radius float

    private Sphere() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 0.0f, 0.0f, 1.0f);
        return ptr;
    }

    public static long allocate(float cx, float cy, float cz, float radius)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, cx, cy, cz, radius);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static long getCenterPtr(long ptr)
    {
        return ptr;
    }

    public static float getRadius(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 12L);
    }

    public static void setRadius(long ptr, float radius)
    {
        ForeignMemory.putFloat(ptr + 12L, radius);
    }

    public static void set(long ptr, float cx, float cy, float cz, float radius)
    {
        Vec3.set(ptr, cx, cy, cz);
        ForeignMemory.putFloat(ptr + 12L, radius);
    }

    public static boolean containsPoint(long spherePtr, float x, float y, float z)
    {
        float dx = x - Vec3.getX(spherePtr);
        float dy = y - Vec3.getY(spherePtr);
        float dz = z - Vec3.getZ(spherePtr);
        float r = getRadius(spherePtr);
        return (dx * dx + dy * dy + dz * dz) <= (r * r);
    }

    /**
     * Algebraic Ray-Sphere Intersection test.
     * @return float hit distance t, or -1.0f if no intersection.
     */
    public static float intersectsRay(long spherePtr, long rayPtr)
    {
        float ox = Vec3.getX(rayPtr), oy = Vec3.getY(rayPtr), oz = Vec3.getZ(rayPtr);
        float dx = Vec3.getX(rayPtr + 12L), dy = Vec3.getY(rayPtr + 12L), dz = Vec3.getZ(rayPtr + 12L);

        float cx = Vec3.getX(spherePtr), cy = Vec3.getY(spherePtr), cz = Vec3.getZ(spherePtr);
        float r = getRadius(spherePtr);

        float lx = cx - ox, ly = cy - oy, lz = cz - oz;
        float tca = lx * dx + ly * dy + lz * dz;
        if (tca < 0.0f) return -1.0f;

        float d2 = (lx * lx + ly * ly + lz * lz) - tca * tca;
        float r2 = r * r;
        if (d2 > r2) return -1.0f;

        float thc = (float) Math.sqrt(r2 - d2);
        float t0 = tca - thc;
        float t1 = tca + thc;

        return t0 > FastMath.EPSILON ? t0 : (t1 > FastMath.EPSILON ? t1 : -1.0f);
    }

    public static String toString(long ptr)
    {
        return "Sphere[Center: " + Vec3.toString(ptr) + ", Radius: " + getRadius(ptr) + "]";
    }
}
