package physics;

import annotation.Draft;
import annotation.Intention;
import annotation.Unsafe;
import annotation.Volatile;
import lang.Vec3;
import nio.ForeignMemory;

/**
 * Memory mapper for an off-heap Physics Body struct.
 * Stride is exactly 64 bytes (8 longs), aligning perfectly with CPU cache lines.
 * Provides Safe (null-checked), Volatile (thread-safe), Unsafe (raw), and Unsafe-Volatile access variants.
 */
@Draft
@Intention("Off-heap static body data accessor supporting safe, volatile, and unsafe variants")
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

    private static void checkPointer(long ptr)
    {
        if (ptr == 0L)
        {
            throw new NullPointerException("Accessing NULL physics body pointer!");
        }
    }

    // ==========================================
    // 1. SAFE ACCESSORS (Null-Checked, Non-Volatile)
    // ==========================================

    public static void getPosition(long bodyPtr, long destVec3Ptr)
    {
        checkPointer(bodyPtr);
        checkPointer(destVec3Ptr);
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 8L)
        );
    }

    public static void setPosition(long bodyPtr, float x, float y, float z)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS, x);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 4L, y);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 8L, z);
    }

    public static float getPositionY(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L);
    }

    public static void getVelocity(long bodyPtr, long destVec3Ptr)
    {
        checkPointer(bodyPtr);
        checkPointer(destVec3Ptr);
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 8L)
        );
    }

    public static void setVelocity(long bodyPtr, float vx, float vy, float vz)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL, vx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 4L, vy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 8L, vz);
    }

    public static float getVelocityY(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L);
    }

    public static float getInverseMass(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloat(bodyPtr + OFFSET_INV_MASS);
    }

    public static void setInverseMass(long bodyPtr, float invMass)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloat(bodyPtr + OFFSET_INV_MASS, invMass);
    }

    public static float getRestitution(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RESTITUTION);
    }

    public static void setRestitution(long bodyPtr, float restitution)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloat(bodyPtr + OFFSET_RESTITUTION, restitution);
    }

    public static float getRadius(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RADIUS);
    }

    public static void setRadius(long bodyPtr, float radius)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloat(bodyPtr + OFFSET_RADIUS, radius);
    }

    public static int getEntityId(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getInt(bodyPtr + OFFSET_ENTITY_ID);
    }

    public static void setEntityId(long bodyPtr, int entityId)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putInt(bodyPtr + OFFSET_ENTITY_ID, entityId);
    }


    // ==========================================
    // 2. VOLATILE ACCESSORS (Null-Checked, Volatile)
    // ==========================================

    @Volatile
    public static void getVolatilePosition(long bodyPtr, long destVec3Ptr)
    {
        checkPointer(bodyPtr);
        checkPointer(destVec3Ptr);
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS), 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS + 4L), 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS + 8L)
        );
    }

    @Volatile
    public static void setVolatilePosition(long bodyPtr, float x, float y, float z)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS, x);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS + 4L, y);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS + 8L, z);
    }

    @Volatile
    public static float getVolatileInverseMass(long bodyPtr)
    {
        checkPointer(bodyPtr);
        return ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_INV_MASS);
    }

    @Volatile
    public static void setVolatileInverseMass(long bodyPtr, float invMass)
    {
        checkPointer(bodyPtr);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_INV_MASS, invMass);
    }


    // ==========================================
    // 3. UNSAFE ACCESSORS (Bypasses Null-Checks, Non-Volatile)
    // ==========================================

    @Unsafe
    public static void unsafeGetPosition(long bodyPtr, long destVec3Ptr)
    {
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 8L)
        );
    }

    @Unsafe
    public static void unsafeSetPosition(long bodyPtr, float x, float y, float z)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS, x);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 4L, y);
        ForeignMemory.putFloat(bodyPtr + OFFSET_POS + 8L, z);
    }

    @Unsafe
    public static float unsafeGetPositionX(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS);
    }

    @Unsafe
    public static float unsafeGetPositionY(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 4L);
    }

    @Unsafe
    public static float unsafeGetPositionZ(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_POS + 8L);
    }

    @Unsafe
    public static void unsafeGetVelocity(long bodyPtr, long destVec3Ptr)
    {
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 8L)
        );
    }

    @Unsafe
    public static void unsafeSetVelocity(long bodyPtr, float vx, float vy, float vz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL, vx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 4L, vy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_VEL + 8L, vz);
    }

    @Unsafe
    public static float unsafeGetVelocityX(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL);
    }

    @Unsafe
    public static float unsafeGetVelocityY(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 4L);
    }

    @Unsafe
    public static float unsafeGetVelocityZ(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_VEL + 8L);
    }

    @Unsafe
    public static void unsafeGetForce(long bodyPtr, long destVec3Ptr)
    {
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_FORCE + 8L)
        );
    }

    @Unsafe
    public static void unsafeSetForce(long bodyPtr, float fx, float fy, float fz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE, fx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 4L, fy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_FORCE + 8L, fz);
    }

    @Unsafe
    public static float unsafeGetInverseMass(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_INV_MASS);
    }

    @Unsafe
    public static void unsafeSetInverseMass(long bodyPtr, float invMass)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_INV_MASS, invMass);
    }

    @Unsafe
    public static float unsafeGetRestitution(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RESTITUTION);
    }

    @Unsafe
    public static void unsafeSetRestitution(long bodyPtr, float restitution)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_RESTITUTION, restitution);
    }

    @Unsafe
    public static float unsafeGetRadius(long bodyPtr)
    {
        return ForeignMemory.getFloat(bodyPtr + OFFSET_RADIUS);
    }

    @Unsafe
    public static void unsafeSetRadius(long bodyPtr, float radius)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_RADIUS, radius);
    }

    @Unsafe
    public static void unsafeGetAabbHalfExtents(long bodyPtr, long destVec3Ptr)
    {
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF + 4L), 
            ForeignMemory.getFloat(bodyPtr + OFFSET_AABB_HALF + 8L)
        );
    }

    @Unsafe
    public static void unsafeSetAabbHalfExtents(long bodyPtr, float dx, float dy, float dz)
    {
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF, dx);
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF + 4L, dy);
        ForeignMemory.putFloat(bodyPtr + OFFSET_AABB_HALF + 8L, dz);
    }

    @Unsafe
    public static int unsafeGetEntityId(long bodyPtr)
    {
        return ForeignMemory.getInt(bodyPtr + OFFSET_ENTITY_ID);
    }

    @Unsafe
    public static void unsafeSetEntityId(long bodyPtr, int entityId)
    {
        ForeignMemory.putInt(bodyPtr + OFFSET_ENTITY_ID, entityId);
    }


    // ==========================================
    // 4. UNSAFE VOLATILE ACCESSORS (Bypasses Checks, Volatile)
    // ==========================================

    @Unsafe
    @Volatile
    public static void unsafeVolatileGetPosition(long bodyPtr, long destVec3Ptr)
    {
        Vec3.set(destVec3Ptr, 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS), 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS + 4L), 
            ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_POS + 8L)
        );
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetPosition(long bodyPtr, float x, float y, float z)
    {
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS, x);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS + 4L, y);
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_POS + 8L, z);
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGetInverseMass(long bodyPtr)
    {
        return ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_INV_MASS);
    }

    @Unsafe
    @Volatile
    public static void unsafeVolatileSetInverseMass(long bodyPtr, float invMass)
    {
        ForeignMemory.putFloatVolatile(bodyPtr + OFFSET_INV_MASS, invMass);
    }

    @Unsafe
    @Volatile
    public static float unsafeVolatileGetVelocityY(long bodyPtr)
    {
        return ForeignMemory.getFloatVolatile(bodyPtr + OFFSET_VEL + 4L);
    }
}
