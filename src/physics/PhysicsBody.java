package physics;

import annotation.Draft;
import annotation.Intention;
import lang.Vec3;
import nio.ForeignMemory;

/**
 * Static memory mapper for an off-heap Physics Body struct.
 * Stride is exactly 64 bytes (8 longs), aligning perfectly with CPU cache lines.
 * Contains position, velocity, forces, mass descriptors, collision bounds, and target entity IDs.
 */
@Draft
@Intention("Off-heap static body data accessor mapping 64 bytes of native memory layout")
public final class PhysicsBody
{
    public static final long BYTES = 64L; // 8 longs * 8 bytes

    // Field offsets inside the 64-byte block
    public static final long OFFSET_POS = 0L;              // 12 bytes (3 floats)
    public static final long OFFSET_VEL = 12L;             // 12 bytes (3 floats)
    public static final long OFFSET_FORCE = 24L;           // 12 bytes (3 floats)
    public static final long OFFSET_INV_MASS = 36L;        // 4 bytes (float)
    public static final long OFFSET_RESTITUTION = 40L;    // 4 bytes (float)
    public static final long OFFSET_RADIUS = 44L;          // 4 bytes (float)
    public static final long OFFSET_AABB_HALF = 48L;       // 12 bytes (3 floats)
    public static final long OFFSET_ENTITY_ID = 60L;       // 4 bytes (int)

    private PhysicsBody() {}

    public static void getPosition(long bodyPtr, long destVec3Ptr)
    {
        float x = ForeignMemory.getFloat(bodyPtr + OFFSET_POS);
        float y = ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L);
        float z = ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 8L);
        Vec3.set(destVec3Ptr, x, y, z);
    }

    public static void setPosition(long bodyPtr, float x, float y, float z)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS, x);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 4L, y);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 8L, z);
    }

    public static float getPositionX(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS);
    }

    public static float getPositionY(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L);
    }

    public static float getPositionZ(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 8L);
    }

    public static void getVelocity(long bodyPtr, long destVec3Ptr)
    {
        float x = ForeignMemory.getFloat(bodyPtr + OFFSET_VEL);
        float y = ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L);
        float z = ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 8L);
        Vec3.set(destVec3Ptr, x, y, z);
    }

    public static void setVelocity(long bodyPtr, float vx, float vy, float vz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL, vx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 4L, vy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 8L, vz);
    }

    public static float getVelocityX(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL);
    }

    public static float getVelocityY(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L);
    }

    public static float getVelocityZ(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 8L);
    }

    public static void getForce(long bodyPtr, long destVec3Ptr)
    {
        float x = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE);
        float y = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 4L);
        float z = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 8L);
        Vec3.set(destVec3Ptr, x, y, z);
    }

    public static void setForce(long bodyPtr, float fx, float fy, float fz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE, fx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 4L, fy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 8L, fz);
    }

    public static void addForce(long bodyPtr, float fx, float fy, float fz)
    {
        float curX = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE);
        float curY = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 4L);
        float curZ = ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 8L);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE, curX + fx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 4L, curY + fy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 8L, curZ + fz);
    }

    public static float getInverseMass(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_INV_MASS);
    }

    public static void setInverseMass(long bodyPtr, float invMass)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_INV_MASS, invMass);
    }

    public static float getRestitution(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RESTITUTION);
    }

    public static void setRestitution(long bodyPtr, float restitution)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_RESTITUTION, restitution);
    }

    public static float getRadius(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RADIUS);
    }

    public static void setRadius(long bodyPtr, float radius)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_RADIUS, radius);
    }

    public static void getAabbHalfExtents(long bodyPtr, long destVec3Ptr)
    {
        float x = ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF);
        float y = ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF + 4L);
        float z = ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF + 8L);
        Vec3.set(destVec3Ptr, x, y, z);
    }

    public static void setAabbHalfExtents(long bodyPtr, float dx, float dy, float dz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF, dx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF + 4L, dy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF + 8L, dz);
    }

    public static int getEntityId(long bodyPtr)
    {
        return ForeignMemory.getInt(bodyPtr + OFFSET_ENTITY_ID);
    }

    public static void setEntityId(long bodyPtr, int entityId)
    {
        ForeignMemory.putInt(bodyPtr + OFFSET_ENTITY_ID, entityId);
    }
}
