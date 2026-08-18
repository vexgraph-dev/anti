package math;

import lang.Vec3;
import nio.ForeignMemory;

import nio.StringLookup;
/**
 * Off-Heap 3D Ray representation (Origin Vec3 + Direction Vec3) stored in contiguous 24-byte native memory.
 * Designed for zero-GC allocation, raytracing, and picking query performance.
 */
public final class Ray
{
    public static final long BYTES = 24L; // 12B Origin + 12B Direction

    private Ray() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return ptr;
    }

    public static long allocate(float ox, float oy, float oz, float dx, float dy, float dz)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, ox, oy, oz, dx, dy, dz);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static long getOriginPtr(long ptr)
    {
        return ptr;
    }

    public static long getDirectionPtr(long ptr)
    {
        return ptr + 12L;
    }

    public static void setOrigin(long ptr, float x, float y, float z)
    {
        Vec3.set(ptr, x, y, z);
    }

    public static void setDirection(long ptr, float dx, float dy, float dz)
    {
        Vec3.set(ptr + 12L, dx, dy, dz);
        Vec3.normalize(ptr + 12L, ptr + 12L);
    }

    public static void set(long ptr, float ox, float oy, float oz, float dx, float dy, float dz)
    {
        setOrigin(ptr, ox, oy, oz);
        setDirection(ptr, dx, dy, dz);
    }

    /**
     * Calculates Point P(t) = Origin + t * Direction.
     */
    public static void getPointAt(long destVec3Ptr, long rayPtr, float t)
    {
        float ox = Vec3.getX(rayPtr);
        float oy = Vec3.getY(rayPtr);
        float oz = Vec3.getZ(rayPtr);

        float dx = Vec3.getX(rayPtr + 12L);
        float dy = Vec3.getY(rayPtr + 12L);
        float dz = Vec3.getZ(rayPtr + 12L);

        Vec3.set(destVec3Ptr, ox + t * dx, oy + t * dy, oz + t * dz);
    }

    public static String toString(long ptr)
    {
        return StringLookup.getJavaString(379) + Vec3.toString(ptr) + StringLookup.getJavaString(380) + Vec3.toString(ptr + 12L) + StringLookup.getJavaString(67);
    }
}
