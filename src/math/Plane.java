package math;

import lang.FastMath;
import lang.Vec3;
import nio.ForeignMemory;

import nio.StringLookup;
/**
 * Off-Heap 3D Plane representation (Normal Vec3 + Distance float d) stored in 16-byte native memory.
 * Equation: Normal . P + Distance = 0.
 * Designed for zero-GC allocation, camera frustum clipping, and spatial partition planes.
 */
public final class Plane
{
    public static final long BYTES = 16L; // 12B Normal Vec3 + 4B Distance float

    private Plane() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 1.0f, 0.0f, 0.0f);
        return ptr;
    }

    public static long allocate(float nx, float ny, float nz, float distance)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, nx, ny, nz, distance);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static long getNormalPtr(long ptr)
    {
        return ptr;
    }

    public static float getDistance(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 12L);
    }

    public static void setDistance(long ptr, float distance)
    {
        ForeignMemory.setFloat(ptr + 12L, distance);
    }

    public static void set(long ptr, float nx, float ny, float nz, float distance)
    {
        Vec3.set(ptr, nx, ny, nz);
        Vec3.normalize(ptr, ptr);
        ForeignMemory.setFloat(ptr + 12L, distance);
    }

    public static float distanceToPoint(long planePtr, float x, float y, float z)
    {
        float nx = Vec3.getX(planePtr), ny = Vec3.getY(planePtr), nz = Vec3.getZ(planePtr);
        float d = getDistance(planePtr);
        return nx * x + ny * y + nz * z + d;
    }

    public static float intersectsRay(long planePtr, long rayPtr)
    {
        float nx = Vec3.getX(planePtr), ny = Vec3.getY(planePtr), nz = Vec3.getZ(planePtr);
        float d = getDistance(planePtr);

        float ox = Vec3.getX(rayPtr), oy = Vec3.getY(rayPtr), oz = Vec3.getZ(rayPtr);
        float dx = Vec3.getX(rayPtr + 12L), dy = Vec3.getY(rayPtr + 12L), dz = Vec3.getZ(rayPtr + 12L);

        float denom = nx * dx + ny * dy + nz * dz;
        if (FastMath.abs(denom) < FastMath.EPSILON) return -1.0f;

        float t = -(nx * ox + ny * oy + nz * oz + d) / denom;
        return t >= 0.0f ? t : -1.0f;
    }

    public static String toString(long ptr)
    {
        return StringLookup.getJavaString(385) + Vec3.toString(ptr) + StringLookup.getJavaString(386) + getDistance(ptr) + StringLookup.getJavaString(67);
    }
}
