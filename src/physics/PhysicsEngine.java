package physics;

import annotation.Draft;
import annotation.Intention;
import lang.FastMath;
import nio.ForeignMemory;

/**
 * High-performance, zero-allocation Data-Oriented Design (DOD) Physics Engine.
 * Manages an off-heap contiguous block of Physics Bodies.
 * Executes symplectic Euler integration and resolves Sphere-Sphere, AABB-AABB, and Sphere-AABB collisions.
 */
@Draft
@Intention("Core DOD physics solver executing integration and impulse-based collision resolution off-heap")
public final class PhysicsEngine
{
    private static long bodiesBlockPtr = 0L;
    private static int bodyCount = 0;
    private static int maxBodies = 0;
    
    private static float gravityX = 0.0f;
    private static float gravityY = -9.81f;
    private static float gravityZ = 0.0f;

    // Positional correction factors (linear projection to prevent sinking/overlap jitter)
    private static final float PENETRATION_ALLOWANCE = 0.01f; // Slop
    private static final float PENETRATION_CORRECTION_PERCENT = 0.2f; // Baumgarte

    private PhysicsEngine() {}

    /**
     * Initializes the physics engine with maximum body capacity.
     */
    public static void init(int capacity)
    {
        maxBodies = capacity;
        bodiesBlockPtr = ForeignMemory.allocateNative(capacity * PhysicsBody.BYTES);
        bodyCount = 0;
        
        // Zero out memory block
        long totalBytes = capacity * PhysicsBody.BYTES;
        for (long i = 0; i < totalBytes; i++)
        {
            ForeignMemory.putByte(bodiesBlockPtr + i, (byte)0);
        }
        
        System.out.println("[Physics] Engine initialized with capacity: " + capacity);
    }

    public static void setGravity(float x, float y, float z)
    {
        gravityX = x;
        gravityY = y;
        gravityZ = z;
    }

    public static int getBodyCount()
    {
        return bodyCount;
    }

    public static long getBodyPointer(int index)
    {
        if (index < 0 || index >= bodyCount) return 0L;
        return bodiesBlockPtr + index * PhysicsBody.BYTES;
    }

    /**
     * Registers a new physics body inside the off-heap block.
     * Returns the memory pointer to the newly allocated body.
     */
    public static long addBody(
        float posX, float posY, float posZ,
        float vx, float vy, float vz,
        float invMass, float restitution, float radius,
        float halfX, float halfY, float halfZ,
        int entityId
    ) {
        if (bodyCount >= maxBodies)
        {
            throw new RuntimeException("[Physics ERROR] Exceeded maximum body capacity: " + maxBodies);
        }

        long ptr = bodiesBlockPtr + bodyCount * PhysicsBody.BYTES;
        PhysicsBody.setPosition(ptr, posX, posY, posZ);
        PhysicsBody.setVelocity(ptr, vx, vy, vz);
        PhysicsBody.setForce(ptr, 0.0f, 0.0f, 0.0f);
        PhysicsBody.setInverseMass(ptr, invMass);
        PhysicsBody.setRestitution(ptr, restitution);
        PhysicsBody.setRadius(ptr, radius);
        PhysicsBody.setAabbHalfExtents(ptr, halfX, halfY, halfZ);
        PhysicsBody.setEntityId(ptr, entityId);

        bodyCount++;
        return ptr;
    }

    /**
     * Performs a single physics tick step.
     */
    public static void step(float dt)
    {
        if (bodyCount == 0 || dt <= 0.0f) return;

        // 1. Integration Pass (Symplectic Euler)
        for (int i = 0; i < bodyCount; i++)
        {
            long ptr = bodiesBlockPtr + i * PhysicsBody.BYTES;
            float invMass = PhysicsBody.getInverseMass(ptr);
            if (invMass <= 0.0f) continue; // Static body

            // Fetch forces
            float fx = ForeignMemory.getFloat(ptr + PhysicsBody.OFFSET_FORCE);
            float fy = ForeignMemory.getFloat(ptr + PhysicsBody.OFFSET_FORCE + 4L);
            float fz = ForeignMemory.getFloat(ptr + PhysicsBody.OFFSET_FORCE + 8L);

            // Compute velocities (Velocity += acceleration * dt)
            float vx = PhysicsBody.getVelocityX(ptr) + (fx * invMass + gravityX) * dt;
            float vy = PhysicsBody.getVelocityY(ptr) + (fy * invMass + gravityY) * dt;
            float vz = PhysicsBody.getVelocityZ(ptr) + (fz * invMass + gravityZ) * dt;
            PhysicsBody.setVelocity(ptr, vx, vy, vz);

            // Compute positions (Position += velocity * dt)
            float px = PhysicsBody.getPositionX(ptr) + vx * dt;
            float py = PhysicsBody.getPositionY(ptr) + vy * dt;
            float pz = PhysicsBody.getPositionZ(ptr) + vz * dt;
            PhysicsBody.setPosition(ptr, px, py, pz);

            // Clear forces for next tick
            PhysicsBody.setForce(ptr, 0.0f, 0.0f, 0.0f);
        }

        // 2. Collision Pass (O(N^2) Narrowphase checks)
        for (int i = 0; i < bodyCount; i++)
        {
            long ptrA = bodiesBlockPtr + i * PhysicsBody.BYTES;
            for (int j = i + 1; j < bodyCount; j++)
            {
                long ptrB = bodiesBlockPtr + j * PhysicsBody.BYTES;
                resolveCollision(ptrA, ptrB);
            }
        }
    }

    /**
     * Narrowphase collision detection and impulse-based resolution dispatcher.
     */
    private static void resolveCollision(long ptrA, long ptrB)
    {
        float invMassA = PhysicsBody.getInverseMass(ptrA);
        float invMassB = PhysicsBody.getInverseMass(ptrB);
        if (invMassA == 0.0f && invMassB == 0.0f) return; // Both static

        float radiusA = PhysicsBody.getRadius(ptrA);
        float radiusB = PhysicsBody.getRadius(ptrB);

        boolean isSphereA = radiusA > 0.0f;
        boolean isSphereB = radiusB > 0.0f;

        if (isSphereA && isSphereB)
        {
            resolveSphereSphere(ptrA, ptrB, radiusA, radiusB, invMassA, invMassB);
        }
        else if (!isSphereA && !isSphereB)
        {
            resolveAabbAabb(ptrA, ptrB, invMassA, invMassB);
        }
        else
        {
            // One sphere, one AABB
            if (isSphereA)
            {
                resolveSphereAabb(ptrA, ptrB, radiusA, invMassA, invMassB);
            }
            else
            {
                // B is the sphere, A is the AABB. Reverse normal vector mapping.
                resolveSphereAabb(ptrB, ptrA, radiusB, invMassB, invMassA);
            }
        }
    }

    private static void resolveSphereSphere(long ptrA, long ptrB, float rA, float rB, float invMassA, float invMassB)
    {
        float ax = PhysicsBody.getPositionX(ptrA), ay = PhysicsBody.getPositionY(ptrA), az = PhysicsBody.getPositionZ(ptrA);
        float bx = PhysicsBody.getPositionX(ptrB), by = PhysicsBody.getPositionY(ptrB), bz = PhysicsBody.getPositionZ(ptrB);

        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;

        float distSqr = dx * dx + dy * dy + dz * dz;
        float combinedRadii = rA + rB;

        if (distSqr >= combinedRadii * combinedRadii) return;

        float dist = (float) Math.sqrt(distSqr);
        float normalX = 1.0f, normalY = 0.0f, normalZ = 0.0f;
        if (dist > FastMath.EPSILON)
        {
            normalX = dx / dist;
            normalY = dy / dist;
            normalZ = dz / dist;
        }

        float penetration = combinedRadii - dist;
        applyImpulse(ptrA, ptrB, normalX, normalY, normalZ, penetration, invMassA, invMassB);
    }

    private static void resolveAabbAabb(long ptrA, long ptrB, float invMassA, float invMassB)
    {
        float ax = PhysicsBody.getPositionX(ptrA), ay = PhysicsBody.getPositionY(ptrA), az = PhysicsBody.getPositionZ(ptrA);
        float bx = PhysicsBody.getPositionX(ptrB), by = PhysicsBody.getPositionY(ptrB), bz = PhysicsBody.getPositionZ(ptrB);

        float hx_a = ForeignMemory.getFloat(ptrA + PhysicsBody.OFFSET_AABB_HALF);
        float hy_a = ForeignMemory.getFloat(ptrA + PhysicsBody.OFFSET_AABB_HALF + 4L);
        float hz_a = ForeignMemory.getFloat(ptrA + PhysicsBody.OFFSET_AABB_HALF + 8L);

        float hx_b = ForeignMemory.getFloat(ptrB + PhysicsBody.OFFSET_AABB_HALF);
        float hy_b = ForeignMemory.getFloat(ptrB + PhysicsBody.OFFSET_AABB_HALF + 4L);
        float hz_b = ForeignMemory.getFloat(ptrB + PhysicsBody.OFFSET_AABB_HALF + 8L);

        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;

        float overlapX = (hx_a + hx_b) - FastMath.abs(dx);
        if (overlapX <= 0.0f) return;

        float overlapY = (hy_a + hy_b) - FastMath.abs(dy);
        if (overlapY <= 0.0f) return;

        float overlapZ = (hz_a + hz_b) - FastMath.abs(dz);
        if (overlapZ <= 0.0f) return;

        // Minimum translation vector axis
        float normalX = 0.0f, normalY = 0.0f, normalZ = 0.0f;
        float penetration;

        if (overlapX < overlapY && overlapX < overlapZ)
        {
            penetration = overlapX;
            normalX = dx > 0.0f ? 1.0f : -1.0f;
        }
        else if (overlapY < overlapZ)
        {
            penetration = overlapY;
            normalY = dy > 0.0f ? 1.0f : -1.0f;
        }
        else
        {
            penetration = overlapZ;
            normalZ = dz > 0.0f ? 1.0f : -1.0f;
        }

        applyImpulse(ptrA, ptrB, normalX, normalY, normalZ, penetration, invMassA, invMassB);
    }

    private static void resolveSphereAabb(long ptrSphere, long ptrAabb, float sphereRad, float invMassSphere, float invMassAabb)
    {
        float sx = PhysicsBody.getPositionX(ptrSphere), sy = PhysicsBody.getPositionY(ptrSphere), sz = PhysicsBody.getPositionZ(ptrSphere);
        float bx = PhysicsBody.getPositionX(ptrAabb), by = PhysicsBody.getPositionY(ptrAabb), bz = PhysicsBody.getPositionZ(ptrAabb);

        float hx = ForeignMemory.getFloat(ptrAabb + PhysicsBody.OFFSET_AABB_HALF);
        float hy = ForeignMemory.getFloat(ptrAabb + PhysicsBody.OFFSET_AABB_HALF + 4L);
        float hz = ForeignMemory.getFloat(ptrAabb + PhysicsBody.OFFSET_AABB_HALF + 8L);

        // Clamp sphere center to AABB bounds to find closest point
        float closestX = FastMath.clamp(sx, bx - hx, bx + hx);
        float closestY = FastMath.clamp(sy, by - hy, by + hy);
        float closestZ = FastMath.clamp(sz, bz - hz, bz + hz);

        float dx = sx - closestX;
        float dy = sy - closestY;
        float dz = sz - closestZ;

        float distSqr = dx * dx + dy * dy + dz * dz;
        if (distSqr >= sphereRad * sphereRad) return; // No intersection

        float dist = (float) Math.sqrt(distSqr);
        float normalX, normalY, normalZ;
        float penetration;

        if (dist > FastMath.EPSILON)
        {
            normalX = dx / dist;
            normalY = dy / dist;
            normalZ = dz / dist;
            penetration = sphereRad - dist;
        }
        else
        {
            // Sphere center is inside the AABB. Resolve along min overlap axis to push it out.
            float overlapX = hx - FastMath.abs(sx - bx);
            float overlapY = hy - FastMath.abs(sy - by);
            float overlapZ = hz - FastMath.abs(sz - bz);

            if (overlapX < overlapY && overlapX < overlapZ)
            {
                normalX = (sx - bx) > 0.0f ? 1.0f : -1.0f;
                normalY = 0.0f;
                normalZ = 0.0f;
                penetration = sphereRad + overlapX;
            }
            else if (overlapY < overlapZ)
            {
                normalX = 0.0f;
                normalY = (sy - by) > 0.0f ? 1.0f : -1.0f;
                normalZ = 0.0f;
                penetration = sphereRad + overlapY;
            }
            else
            {
                normalX = 0.0f;
                normalY = 0.0f;
                normalZ = (sz - bz) > 0.0f ? 1.0f : -1.0f;
                penetration = sphereRad + overlapZ;
            }
        }

        // Apply impulse (note normal points from closest point on AABB towards sphere)
        applyImpulse(ptrAabb, ptrSphere, normalX, normalY, normalZ, penetration, invMassAabb, invMassSphere);
    }

    /**
     * Resolves velocities and corrections via impulse physics.
     * Normal vector points from Body A towards Body B.
     */
    private static void applyImpulse(
        long ptrA, long ptrB, 
        float normalX, float normalY, float normalZ, 
        float penetration, 
        float invMassA, float invMassB
    ) {
        float vax = PhysicsBody.getVelocityX(ptrA), vay = PhysicsBody.getVelocityY(ptrA), vaz = PhysicsBody.getVelocityZ(ptrA);
        float vbx = PhysicsBody.getVelocityX(ptrB), vby = PhysicsBody.getVelocityY(ptrB), vbz = PhysicsBody.getVelocityZ(ptrB);

        // Relative velocity
        float rvx = vbx - vax;
        float rvy = vby - vay;
        float rvz = vbz - vaz;

        // Relative velocity along normal
        float velAlongNormal = rvx * normalX + rvy * normalY + rvz * normalZ;

        // If separating, do not apply impulse
        if (velAlongNormal >= 0.0f) return;

        // Restitution (bounciness factor, we take the minimum of both bodies)
        float restitution = Math.min(PhysicsBody.getRestitution(ptrA), PhysicsBody.getRestitution(ptrB));

        // Scalar impulse
        float totalInverseMass = invMassA + invMassB;
        float j = -(1.0f + restitution) * velAlongNormal / totalInverseMass;

        // Apply impulse to velocities
        PhysicsBody.setVelocity(ptrA, vax - normalX * invMassA * j, vay - normalY * invMassA * j, vaz - normalZ * invMassA * j);
        PhysicsBody.setVelocity(ptrB, vbx + normalX * invMassB * j, vby + normalY * invMassB * j, vbz + normalZ * invMassB * j);

        // 3. Positional Correction to prevent sinking/overlap jitter
        float correctionMagnitude = Math.max(penetration - PENETRATION_ALLOWANCE, 0.0f) / totalInverseMass * PENETRATION_CORRECTION_PERCENT;
        float cx = correctionMagnitude * normalX;
        float cy = correctionMagnitude * normalY;
        float cz = correctionMagnitude * normalZ;

        float pax = PhysicsBody.getPositionX(ptrA), pay = PhysicsBody.getPositionY(ptrA), paz = PhysicsBody.getPositionZ(ptrA);
        float pbx = PhysicsBody.getPositionX(ptrB), pby = PhysicsBody.getPositionY(ptrB), pbz = PhysicsBody.getPositionZ(ptrB);

        PhysicsBody.setPosition(ptrA, pax - cx * invMassA, pay - cy * invMassA, paz - cz * invMassA);
        PhysicsBody.setPosition(ptrB, pbx + cx * invMassB, pby + cy * invMassB, pbz + cz * invMassB);
    }

    /**
     * Cleans up native resources.
     */
    public static void free()
    {
        if (bodiesBlockPtr != 0L)
        {
            ForeignMemory.freeNative(bodiesBlockPtr);
            bodiesBlockPtr = 0L;
            bodyCount = 0;
            maxBodies = 0;
            System.out.println("[Physics] Engine resources freed.");
        }
    }
}
