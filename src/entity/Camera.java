package entity;

import annotation.Draft;
import lang.FastMath;
import lang.Mat4;
import primitive.Float;

/**
 * Off-heap Camera struct, stored as a 12-float primitive.Float array (48 bytes).
 * <p>
 * Layout (indices into the Float array):
 * <pre>
 *  0..2   position (x, y, z)
 *  3..5   orientation (pitchDeg, yawDeg, rollDeg)
 *  6      fovY (degrees, vertical)
 *  7      aspect (width / height)
 *  8      zNear
 *  9      zFar
 * 10      flags (reserved)
 * 11      reserved (pad to 48 bytes)
 * </pre>
 * View and projection matrices are written into caller-owned {@code lang.Mat4}
 * pointers; the camera itself is pure data. Zero allocation, zero GC.
 */
@Draft
public final class Camera
{
    public static final int FLOATS = 12;
    public static final long BYTES = 48L;

    public static final int POS_X = 0;
    public static final int POS_Y = 1;
    public static final int POS_Z = 2;
    public static final int PITCH_DEG = 3;
    public static final int YAW_DEG = 4;
    public static final int ROLL_DEG = 5;
    public static final int FOV_Y_DEG = 6;
    public static final int ASPECT = 7;
    public static final int Z_NEAR = 8;
    public static final int Z_FAR = 9;
    public static final int FLAGS = 10;

    public static final int FLAG_DIRTY = 1;

    private static final float DEFAULT_FOV_Y = 70.0f;
    private static final float DEFAULT_ASPECT = 16.0f / 9.0f;
    private static final float DEFAULT_Z_NEAR = 0.1f;
    private static final float DEFAULT_Z_FAR = 1000.0f;

    private Camera() {}

    public static long allocate()
    {
        long ptr = Float.allocateArray(FLOATS);
        Float.set(ptr, FOV_Y_DEG, DEFAULT_FOV_Y);
        Float.set(ptr, ASPECT, DEFAULT_ASPECT);
        Float.set(ptr, Z_NEAR, DEFAULT_Z_NEAR);
        Float.set(ptr, Z_FAR, DEFAULT_Z_FAR);
        return ptr;
    }

    public static void free(long ptr)
    {
        Float.free(ptr);
    }

    public static float getX(long ptr) { return Float.get(ptr, POS_X); }
    public static void setX(long ptr, float x) { Float.set(ptr, POS_X, x); }

    public static float getY(long ptr) { return Float.get(ptr, POS_Y); }
    public static void setY(long ptr, float y) { Float.set(ptr, POS_Y, y); }

    public static float getZ(long ptr) { return Float.get(ptr, POS_Z); }
    public static void setZ(long ptr, float z) { Float.set(ptr, POS_Z, z); }

    public static void setPosition(long ptr, float x, float y, float z)
    {
        Float.set(ptr, POS_X, x);
        Float.set(ptr, POS_Y, y);
        Float.set(ptr, POS_Z, z);
    }

    public static float getPitchDeg(long ptr) { return Float.get(ptr, PITCH_DEG); }
    public static void setPitchDeg(long ptr, float pitch) { Float.set(ptr, PITCH_DEG, pitch); }

    public static float getYawDeg(long ptr) { return Float.get(ptr, YAW_DEG); }
    public static void setYawDeg(long ptr, float yaw) { Float.set(ptr, YAW_DEG, yaw); }

    public static float getRollDeg(long ptr) { return Float.get(ptr, ROLL_DEG); }
    public static void setRollDeg(long ptr, float roll) { Float.set(ptr, ROLL_DEG, roll); }

    public static float getFovYDeg(long ptr) { return Float.get(ptr, FOV_Y_DEG); }
    public static void setFovYDeg(long ptr, float fovY) { Float.set(ptr, FOV_Y_DEG, fovY); }

    public static float getAspect(long ptr) { return Float.get(ptr, ASPECT); }
    public static void setAspect(long ptr, float aspect) { Float.set(ptr, ASPECT, aspect); }

    public static float getZNear(long ptr) { return Float.get(ptr, Z_NEAR); }
    public static void setZNear(long ptr, float zNear) { Float.set(ptr, Z_NEAR, zNear); }

    public static float getZFar(long ptr) { return Float.get(ptr, Z_FAR); }
    public static void setZFar(long ptr, float zFar) { Float.set(ptr, Z_FAR, zFar); }

    public static int getFlags(long ptr) { return (int) Float.get(ptr, FLAGS); }
    public static void setFlags(long ptr, int flags) { Float.set(ptr, FLAGS, flags); }

    public static void setDirty(long ptr) { Float.set(ptr, FLAGS, getFlags(ptr) | FLAG_DIRTY); }
    public static boolean isDirty(long ptr) { return (getFlags(ptr) & FLAG_DIRTY) != 0; }

    /**
     * FPS-style view matrix from position + yaw/pitch into the given Mat4.
     * Uses the unrolled {@link Mat4#createViewMatrix} path.
     */
    public static void createView(long destMat, long camera)
    {
        Mat4.createViewMatrix(destMat,
                Float.get(camera, POS_X), Float.get(camera, POS_Y), Float.get(camera, POS_Z),
                Float.get(camera, PITCH_DEG), Float.get(camera, YAW_DEG), Float.get(camera, ROLL_DEG));
        clearDirty(camera);
    }

    /**
     * Vulkan-friendly perspective projection (flipped Y, reversed-Z-compatible) into the given Mat4.
     */
    public static void createProjection(long destMat, long camera)
    {
        float fovRad = Float.get(camera, FOV_Y_DEG) * FastMath.DEG_TO_RAD;
        Mat4.perspectiveVulkan(destMat, fovRad,
                Float.get(camera, ASPECT),
                Float.get(camera, Z_NEAR),
                Float.get(camera, Z_FAR));
    }

    public static void clearDirty(long ptr) { Float.set(ptr, FLAGS, getFlags(ptr) & ~FLAG_DIRTY); }
}
