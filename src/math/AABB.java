package math;

import lang.FastMath;
import lang.Vec3;
import nio.ForeignMemory;

import nio.StringLookup;
/**
 * Off-Heap Axis-Aligned Bounding Box (AABB) stored in 24-byte native memory (Min Vec3 + Max Vec3).
 * Zero-GC allocation for physics collision and ray-box acceleration structures.
 */
public final class AABB
{
    public static final long BYTES = 24L; // 12B Min + 12B Max

    private AABB() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        return ptr;
    }

    public static long allocate(float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, minX, minY, minZ, maxX, maxY, maxZ);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static long getMinPtr(long ptr)
    {
        return ptr;
    }

    public static long getMaxPtr(long ptr)
    {
        return ptr + 12L;
    }

    public static void set(long ptr, float minX, float minY, float minZ, float maxX, float maxY, float maxZ)
    {
        Vec3.set(ptr, minX, minY, minZ);
        Vec3.set(ptr + 12L, maxX, maxY, maxZ);
    }

    public static void getCenter(long destVec3Ptr, long aabbPtr)
    {
        float minX = Vec3.getX(aabbPtr), minY = Vec3.getY(aabbPtr), minZ = Vec3.getZ(aabbPtr);
        float maxX = Vec3.getX(aabbPtr + 12L), maxY = Vec3.getY(aabbPtr + 12L), maxZ = Vec3.getZ(aabbPtr + 12L);
        Vec3.set(destVec3Ptr, (minX + maxX) * 0.5f, (minY + maxY) * 0.5f, (minZ + maxZ) * 0.5f);
    }

    public static void getExtents(long destVec3Ptr, long aabbPtr)
    {
        float minX = Vec3.getX(aabbPtr), minY = Vec3.getY(aabbPtr), minZ = Vec3.getZ(aabbPtr);
        float maxX = Vec3.getX(aabbPtr + 12L), maxY = Vec3.getY(aabbPtr + 12L), maxZ = Vec3.getZ(aabbPtr + 12L);
        Vec3.set(destVec3Ptr, (maxX - minX) * 0.5f, (maxY - minY) * 0.5f, (maxZ - minZ) * 0.5f);
    }

    public static boolean containsPoint(long aabbPtr, float x, float y, float z)
    {
        return x >= Vec3.getX(aabbPtr) && x <= Vec3.getX(aabbPtr + 12L) &&
               y >= Vec3.getY(aabbPtr) && y <= Vec3.getY(aabbPtr + 12L) &&
               z >= Vec3.getZ(aabbPtr) && z <= Vec3.getZ(aabbPtr + 12L);
    }

    public static boolean intersectsAABB(long aPtr, long bPtr)
    {
        return Vec3.getX(aPtr) <= Vec3.getX(bPtr + 12L) && Vec3.getX(aPtr + 12L) >= Vec3.getX(bPtr) &&
               Vec3.getY(aPtr) <= Vec3.getY(bPtr + 12L) && Vec3.getY(aPtr + 12L) >= Vec3.getY(bPtr) &&
               Vec3.getZ(aPtr) <= Vec3.getZ(bPtr + 12L) && Vec3.getZ(aPtr + 12L) >= Vec3.getZ(bPtr);
    }

    /**
     * Slab method Ray-AABB intersection test.
     * @return float hit distance t, or -1.0f if no intersection.
     */
    public static float intersectsRay(long aabbPtr, long rayPtr)
    {
        float ox = Vec3.getX(rayPtr), oy = Vec3.getY(rayPtr), oz = Vec3.getZ(rayPtr);
        float dx = Vec3.getX(rayPtr + 12L), dy = Vec3.getY(rayPtr + 12L), dz = Vec3.getZ(rayPtr + 12L);

        float minX = Vec3.getX(aabbPtr), minY = Vec3.getY(aabbPtr), minZ = Vec3.getZ(aabbPtr);
        float maxX = Vec3.getX(aabbPtr + 12L), maxY = Vec3.getY(aabbPtr + 12L), maxZ = Vec3.getZ(aabbPtr + 12L);

        float t1 = (minX - ox) / (FastMath.abs(dx) < FastMath.EPSILON ? FastMath.EPSILON : dx);
        float t2 = (maxX - ox) / (FastMath.abs(dx) < FastMath.EPSILON ? FastMath.EPSILON : dx);
        float t3 = (minY - oy) / (FastMath.abs(dy) < FastMath.EPSILON ? FastMath.EPSILON : dy);
        float t4 = (maxY - oy) / (FastMath.abs(dy) < FastMath.EPSILON ? FastMath.EPSILON : dy);
        float t5 = (minZ - oz) / (FastMath.abs(dz) < FastMath.EPSILON ? FastMath.EPSILON : dz);
        float t6 = (maxZ - oz) / (FastMath.abs(dz) < FastMath.EPSILON ? FastMath.EPSILON : dz);

        float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
        float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));

        if (tmax < 0.0f || tmin > tmax) return -1.0f;
        return tmin < 0.0f ? tmax : tmin;
    }

    public static String toString(long ptr)
    {
        return StringLookup.getJavaString(383) + Vec3.toString(ptr) + StringLookup.getJavaString(384) + Vec3.toString(ptr + 12L) + StringLookup.getJavaString(67);
    }
}
