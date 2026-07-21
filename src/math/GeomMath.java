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
     * Ray-Sphere Intersection helper.
     */
    public static float intersectRaySphere(long rayPtr, long spherePtr)
    {
        return Sphere.intersectsRay(spherePtr, rayPtr);
    }


    /**
     * Calculates the height interpolation of a 2D position on a 3D triangle.
     * Ideal for terrain height generation and collision height queries.
     */
    public static float barryCentric(long p1Ptr, long p2Ptr, long p3Ptr, long pos2DPtr)
    {
        float p1x = Vec3.getX(p1Ptr), p1y = Vec3.getY(p1Ptr), p1z = Vec3.getZ(p1Ptr);
        float p2x = Vec3.getX(p2Ptr), p2y = Vec3.getY(p2Ptr), p2z = Vec3.getZ(p2Ptr);
        float p3x = Vec3.getX(p3Ptr), p3y = Vec3.getY(p3Ptr), p3z = Vec3.getZ(p3Ptr);
        float posX = Vec2.getX(pos2DPtr), posY = Vec2.getY(pos2DPtr);

        float det = (p2z - p3z) * (p1x - p3x) + (p3x - p2x) * (p1z - p3z);
        if (FastMath.abs(det) < FastMath.EPSILON) return 0.0f;

        float l1 = ((p2z - p3z) * (posX - p3x) + (p3x - p2x) * (posY - p3z)) / det;
        float l2 = ((p3z - p1z) * (posX - p3x) + (p1x - p3x) * (posY - p3z)) / det;
        float l3 = 1.0f - l1 - l2;
        return l1 * p1y + l2 * p2y + l3 * p3y;
    }

    /**
     * Calculates the Barycentric coordinates (u, v, w) of a 3D point P relative to triangle ABC.
     * Completely unrolled for ZERO off-heap/heap allocations.
     * @param destVec3Ptr Raw memory address of Vec3 destination to store weights (u, v, w).
     */
    public static void barycentric(long destVec3Ptr, long aPtr, long bPtr, long cPtr, long pPtr)
    {
        float ax = Vec3.getX(aPtr), ay = Vec3.getY(aPtr), az = Vec3.getZ(aPtr);
        float bx = Vec3.getX(bPtr), by = Vec3.getY(bPtr), bz = Vec3.getZ(bPtr);
        float cx = Vec3.getX(cPtr), cy = Vec3.getY(cPtr), cz = Vec3.getZ(cPtr);
        float px = Vec3.getX(pPtr), py = Vec3.getY(pPtr), pz = Vec3.getZ(pPtr);

        float v0x = bx - ax, v0y = by - ay, v0z = bz - az;
        float v1x = cx - ax, v1y = cy - ay, v1z = cz - az;
        float v2x = px - ax, v2y = py - ay, v2z = pz - az;

        float d00 = v0x * v0x + v0y * v0y + v0z * v0z;
        float d01 = v0x * v1x + v0y * v1y + v0z * v1z;
        float d11 = v1x * v1x + v1y * v1y + v1z * v1z;
        float d20 = v2x * v0x + v2y * v0y + v2z * v0z;
        float d21 = v2x * v1x + v2y * v1y + v2z * v1z;

        float denominator = d00 * d11 - d01 * d01;

        if (FastMath.abs(denominator) < FastMath.EPSILON)
        {
            Vec3.set(destVec3Ptr, 0.0f, 0.0f, 0.0f);
            return;
        }

        float invDenominator = 1.0f / denominator;
        float v = ((d11 * d20) - (d01 * d21)) * invDenominator;
        float w = ((d00 * d21) - (d01 * d20)) * invDenominator;
        float u = 1.0f - v - w;

        Vec3.set(destVec3Ptr, u, v, w);
    }

    /**
     * Möller–Trumbow Ray-Triangle Intersection testing.
     * @return Distance 't' along ray direction where intersection occurs, or -1.0f if no hit.
     */
    public static float intersectRayTriangle(long rayOriginPtr, long rayDirPtr, long v0Ptr, long v1Ptr, long v2Ptr)
    {
        float rx = Vec3.getX(rayOriginPtr), ry = Vec3.getY(rayOriginPtr), rz = Vec3.getZ(rayOriginPtr);
        float dx = Vec3.getX(rayDirPtr),    dy = Vec3.getY(rayDirPtr),    dz = Vec3.getZ(rayDirPtr);

        float v0x = Vec3.getX(v0Ptr), v0y = Vec3.getY(v0Ptr), v0z = Vec3.getZ(v0Ptr);
        float v1x = Vec3.getX(v1Ptr), v1y = Vec3.getY(v1Ptr), v1z = Vec3.getZ(v1Ptr);
        float v2x = Vec3.getX(v2Ptr), v2y = Vec3.getY(v2Ptr), v2z = Vec3.getZ(v2Ptr);

        float edge1X = v1x - v0x, edge1Y = v1y - v0y, edge1Z = v1z - v0z;
        float edge2X = v2x - v0x, edge2Y = v2y - v0y, edge2Z = v2z - v0z;

        float hX = dy * edge2Z - dz * edge2Y;
        float hY = dz * edge2X - dx * edge2Z;
        float hZ = dx * edge2Y - dy * edge2X;

        float det = edge1X * hX + edge1Y * hY + edge1Z * hZ;

        if (FastMath.abs(det) < FastMath.EPSILON) return -1.0f;

        float invDet = 1.0f / det;

        float sX = rx - v0x, sY = ry - v0y, sZ = rz - v0z;

        float u = (sX * hX + sY * hY + sZ * hZ) * invDet;
        if (u < 0.0f || u > 1.0f) return -1.0f;

        float qX = sY * edge1Z - sZ * edge1Y;
        float qY = sZ * edge1X - sX * edge1Z;
        float qZ = sX * edge1Y - sY * edge1X;

        float v = (dx * qX + dy * qY + dz * qZ) * invDet;
        if (v < 0.0f || (u + v) > 1.0f) return -1.0f;

        float t = (edge2X * qX + edge2Y * qY + edge2Z * qZ) * invDet;

        return t > FastMath.EPSILON ? t : -1.0f;
    }

    /**
     * Ray-AABB Intersection helper.
     */
    public static float intersectRayAABB(long rayPtr, long aabbPtr)
    {
        return AABB.intersectsRay(aabbPtr, rayPtr);
    }
}
