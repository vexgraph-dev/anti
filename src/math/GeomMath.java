package math;

import lang.FastMath;
import lang.Vec2;
import lang.Vec3;

/**
 * High-performance Geometry and Spatial Raycasting Math Subsystem.
 * Unrolled algebraic calculations operating on raw memory address pointers (long) for zero-allocation and max speed.
 */
public final class GeomMath
{
    private GeomMath() {}

    /**
     * Calculates the height interpolation of a 2D position on a 3D triangle.
     */
    public static float barryCentric(long p1Ptr, long p2Ptr, long p3Ptr, long pos2DPtr)
    {
        return lang.GeomMath.barryCentric(p1Ptr, p2Ptr, p3Ptr, pos2DPtr);
    }

    /**
     * Calculates the Barycentric coordinates (u, v, w) of a 3D point P relative to triangle ABC.
     */
    public static void barycentric(long destVec3Ptr, long aPtr, long bPtr, long cPtr, long pPtr)
    {
        lang.GeomMath.barycentric(destVec3Ptr, aPtr, bPtr, cPtr, pPtr);
    }

    /**
     * Möller–Trumbore Ray-Triangle Intersection testing.
     */
    public static float intersectRayTriangle(long rayOriginPtr, long rayDirPtr, long v0Ptr, long v1Ptr, long v2Ptr)
    {
        return lang.GeomMath.intersectRayTriangle(rayOriginPtr, rayDirPtr, v0Ptr, v1Ptr, v2Ptr);
    }

    /**
     * Ray-Sphere Intersection helper.
     */
    public static float intersectRaySphere(long rayPtr, long spherePtr)
    {
        return Sphere.intersectsRay(spherePtr, rayPtr);
    }

    /**
     * Ray-AABB Intersection helper.
     */
    public static float intersectRayAABB(long rayPtr, long aabbPtr)
    {
        return AABB.intersectsRay(aabbPtr, rayPtr);
    }
}
