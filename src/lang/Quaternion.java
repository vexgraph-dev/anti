package lang;

import nio.ForeignMemory;

/**
 * Off-Heap Quaternion representation (x, y, z, w) stored in 16-byte contiguous native memory.
 * Designed for zero-GC orientation tracking, rotations, slerp, and conversion to Mat4.
 */
public final class Quaternion
{
    public static final long BYTES = 16L; // 4 floats * 4 bytes

    private Quaternion() {}

    public static long allocate()
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        identity(ptr);
        return ptr;
    }

    public static long allocate(float x, float y, float z, float w)
    {
        long ptr = ForeignMemory.allocateNative(BYTES);
        set(ptr, x, y, z, w);
        return ptr;
    }

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr);
    }

    public static float getX(long ptr)
    {
        return ForeignMemory.getFloat(ptr);
    }

    public static void setX(long ptr, float x)
    {
        ForeignMemory.setFloat(ptr, x);
    }

    public static float getY(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 4L);
    }

    public static void setY(long ptr, float y)
    {
        ForeignMemory.setFloat(ptr + 4L, y);
    }

    public static float getZ(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 8L);
    }

    public static void setZ(long ptr, float z)
    {
        ForeignMemory.setFloat(ptr + 8L, z);
    }

    public static float getW(long ptr)
    {
        return ForeignMemory.getFloat(ptr + 12L);
    }

    public static void setW(long ptr, float w)
    {
        ForeignMemory.setFloat(ptr + 12L, w);
    }

    public static void set(long ptr, float x, float y, float z, float w)
    {
        ForeignMemory.setFloat(ptr, x);
        ForeignMemory.setFloat(ptr + 4L, y);
        ForeignMemory.setFloat(ptr + 8L, z);
        ForeignMemory.setFloat(ptr + 12L, w);
    }

    public static void identity(long ptr)
    {
        set(ptr, 0.0f, 0.0f, 0.0f, 1.0f);
    }

    public static void setFromAxisAngle(long ptr, float axisX, float axisY, float axisZ, float angle)
    {
        float halfAngle = angle * 0.5f;
        float sin = FastMath.sin32(halfAngle);
        float cos = FastMath.cos32(halfAngle);
        set(ptr, axisX * sin, axisY * sin, axisZ * sin, cos);
    }

    public static void mul(long destPtr, long aPtr, long bPtr)
    {
        float ax = getX(aPtr), ay = getY(aPtr), az = getZ(aPtr), aw = getW(aPtr);
        float bx = getX(bPtr), by = getY(bPtr), bz = getZ(bPtr), bw = getW(bPtr);

        float rx = aw * bx + ax * bw + ay * bz - az * by;
        float ry = aw * by - ax * bz + ay * bw + az * bx;
        float rz = aw * bz + ax * by - ay * bx + az * bw;
        float rw = aw * bw - ax * bx - ay * by - az * bz;

        set(destPtr, rx, ry, rz, rw);
    }

    public static void normalize(long ptr)
    {
        float x = getX(ptr), y = getY(ptr), z = getZ(ptr), w = getW(ptr);
        float len2 = x * x + y * y + z * z + w * w;
        if (len2 > 0.0f && FastMath.abs(len2 - 1.0f) > FastMath.EPSILON)
        {
            float invLen = FastMath.invSqrt(len2);
            set(ptr, x * invLen, y * invLen, z * invLen, w * invLen);
        }
    }

    public static void slerp(long destPtr, long aPtr, long bPtr, float t)
    {
        float ax = getX(aPtr), ay = getY(aPtr), az = getZ(aPtr), aw = getW(aPtr);
        float bx = getX(bPtr), by = getY(bPtr), bz = getZ(bPtr), bw = getW(bPtr);

        float cosHalfTheta = ax * bx + ay * by + az * bz + aw * bw;

        // If the product is negative, slerp should go the other way around
        if (cosHalfTheta < 0.0f)
        {
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
            cosHalfTheta = -cosHalfTheta;
        }

        if (FastMath.abs(cosHalfTheta) >= 1.0f)
        {
            set(destPtr, ax, ay, az, aw);
            return;
        }

        // Calculate temporary values.
        float halfTheta = (float) Math.acos(cosHalfTheta);
        float sinHalfTheta = (float) Math.sqrt(1.0 - cosHalfTheta * cosHalfTheta);

        // If theta = 180 degrees then result is not unique
        if (FastMath.abs(sinHalfTheta) < 0.001f)
        {
            set(destPtr, 
                ax * (1.0f - t) + bx * t, 
                ay * (1.0f - t) + by * t, 
                az * (1.0f - t) + bz * t, 
                aw * (1.0f - t) + bw * t
            );
            normalize(destPtr);
            return;
        }

        float ratioA = (float) Math.sin((1.0 - t) * halfTheta) / sinHalfTheta;
        float ratioB = (float) Math.sin(t * halfTheta) / sinHalfTheta;

        set(destPtr,
            ax * ratioA + bx * ratioB,
            ay * ratioA + by * ratioB,
            az * ratioA + bz * ratioB,
            aw * ratioA + bw * ratioB
        );
    }

    /**
     * Converts this Quaternion to a 4x4 rotation matrix.
     * Writes into the provided Mat4 pointer (column-major order matching Mat4.java).
     */
    public static void toRotationMatrix(long destMat4Ptr, long quatPtr)
    {
        float x = getX(quatPtr), y = getY(quatPtr), z = getZ(quatPtr), w = getW(quatPtr);

        float xx = x * x;
        float xy = x * y;
        float xz = x * z;
        float xw = x * w;

        float yy = y * y;
        float yz = y * z;
        float yw = y * w;

        float zz = z * z;
        float zw = z * w;

        // Column 0
        Mat4.set(destMat4Ptr, 0, 0, 1.0f - 2.0f * (yy + zz));
        Mat4.set(destMat4Ptr, 1, 0, 2.0f * (xy + zw));
        Mat4.set(destMat4Ptr, 2, 0, 2.0f * (xz - yw));
        Mat4.set(destMat4Ptr, 3, 0, 0.0f);

        // Column 1
        Mat4.set(destMat4Ptr, 0, 1, 2.0f * (xy - zw));
        Mat4.set(destMat4Ptr, 1, 1, 1.0f - 2.0f * (xx + zz));
        Mat4.set(destMat4Ptr, 2, 1, 2.0f * (yz + xw));
        Mat4.set(destMat4Ptr, 3, 1, 0.0f);

        // Column 2
        Mat4.set(destMat4Ptr, 0, 2, 2.0f * (xz + yw));
        Mat4.set(destMat4Ptr, 1, 2, 2.0f * (yz - xw));
        Mat4.set(destMat4Ptr, 2, 2, 1.0f - 2.0f * (xx + yy));
        Mat4.set(destMat4Ptr, 3, 2, 0.0f);

        // Column 3 (Translations and homogeneous scale)
        Mat4.set(destMat4Ptr, 0, 3, 0.0f);
        Mat4.set(destMat4Ptr, 1, 3, 0.0f);
        Mat4.set(destMat4Ptr, 2, 3, 0.0f);
        Mat4.set(destMat4Ptr, 3, 3, 1.0f);
    }

    @Override
    public String toString()
    {
        return "Quaternion(off-heap)";
    }

    public static String toString(long ptr)
    {
        return "Quat[" + getX(ptr) + ", " + getY(ptr) + ", " + getZ(ptr) + ", " + getW(ptr) + "]";
    }
}
