package math;

import lang.Mat4;
import lang.Vec3;
import nio.ForeignMemory;

/**
 * Off-Heap View Frustum representation stored in 96-byte native memory (6 Planes * 16 bytes).
 * Designed for zero-GC camera frustum culling (AABB-Frustum intersection) before Vulkan draw calls.
 */
public final class Frustum
{
    public static final long BYTES = 96L; // 6 Planes * 16B

    public static final int PLANE_LEFT   = 0;
    public static final int PLANE_RIGHT  = 1;
    public static final int PLANE_BOTTOM = 2;
    public static final int PLANE_TOP    = 3;
    public static final int PLANE_NEAR   = 4;
    public static final int PLANE_FAR    = 5;

    private Frustum() {}

    public static long allocate()
    {
        return ForeignMemory.allocateNative(BYTES);
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static long getPlanePtr(long frustumPtr, int planeIndex)
    {
        return frustumPtr + (planeIndex * 16L);
    }

    /**
     * Extracts 6 frustum clipping planes directly from a Vulkan/OpenGL View-Projection matrix.
     */
    public static void extractFromMatrix(long frustumPtr, long viewProjectionMatPtr)
    {
        float m00 = Mat4.get(viewProjectionMatPtr, 0, 0), m01 = Mat4.get(viewProjectionMatPtr, 0, 1), m02 = Mat4.get(viewProjectionMatPtr, 0, 2), m03 = Mat4.get(viewProjectionMatPtr, 0, 3);
        float m10 = Mat4.get(viewProjectionMatPtr, 1, 0), m11 = Mat4.get(viewProjectionMatPtr, 1, 1), m12 = Mat4.get(viewProjectionMatPtr, 1, 2), m13 = Mat4.get(viewProjectionMatPtr, 1, 3);
        float m20 = Mat4.get(viewProjectionMatPtr, 2, 0), m21 = Mat4.get(viewProjectionMatPtr, 2, 1), m22 = Mat4.get(viewProjectionMatPtr, 2, 2), m23 = Mat4.get(viewProjectionMatPtr, 2, 3);
        float m30 = Mat4.get(viewProjectionMatPtr, 3, 0), m31 = Mat4.get(viewProjectionMatPtr, 3, 1), m32 = Mat4.get(viewProjectionMatPtr, 3, 2), m33 = Mat4.get(viewProjectionMatPtr, 3, 3);

        // Left Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_LEFT),   m30 + m00, m31 + m01, m32 + m02, m33 + m03);
        // Right Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_RIGHT),  m30 - m00, m31 - m01, m32 - m02, m33 - m03);
        // Bottom Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_BOTTOM), m30 + m10, m31 + m11, m32 + m12, m33 + m13);
        // Top Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_TOP),    m30 - m10, m31 - m11, m32 - m12, m33 - m13);
        // Near Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_NEAR),   m30 + m20, m31 + m21, m32 + m22, m33 + m23);
        // Far Plane
        Plane.set(getPlanePtr(frustumPtr, PLANE_FAR),    m30 - m20, m31 - m21, m32 - m22, m33 - m23);
    }

    /**
     * Fast AABB-Frustum Intersection Culling Test.
     * @return boolean true if AABB is partially or fully inside the Frustum, false if completely culled.
     */
    public static boolean intersectsAABB(long frustumPtr, long aabbPtr)
    {
        float minX = Vec3.getX(aabbPtr), minY = Vec3.getY(aabbPtr), minZ = Vec3.getZ(aabbPtr);
        float maxX = Vec3.getX(aabbPtr + 12L), maxY = Vec3.getY(aabbPtr + 12L), maxZ = Vec3.getZ(aabbPtr + 12L);

        for (int i = 0; i < 6; i++)
        {
            long planePtr = getPlanePtr(frustumPtr, i);
            float nx = Vec3.getX(planePtr);
            float ny = Vec3.getY(planePtr);
            float nz = Vec3.getZ(planePtr);

            // Compute positive vertex (p-vertex) relative to plane normal
            float px = (nx >= 0.0f) ? maxX : minX;
            float py = (ny >= 0.0f) ? maxY : minY;
            float pz = (nz >= 0.0f) ? maxZ : minZ;

            if (Plane.distanceToPoint(planePtr, px, py, pz) < 0.0f)
            {
                return false; // AABB is completely outside this plane
            }
        }
        return true;
    }
}
