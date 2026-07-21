package relational;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;

/**
 * High-level dynamic dispatcher and query resolver for relational variables.
 */
@Draft
@Intention("Unified dispatcher linking registered symbol payloads to their respective off-heap primitive and struct operations.")
public final class RelationalEngine {

    @Required
    public static final int CLASS_ID = TypeRegister.ID_VARIABLE; // Uses Variable ID as the core layout backing

    private RelationalEngine() {}

    // Dynamic type-checked getters
    public static int getInt32(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT32) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int32 (Class ID: " + cid + ")");
        }
        return primitive.Int32.get(Variable.getPointer(varId));
    }

    public static void setInt32(int varId, int value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT32) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int32 (Class ID: " + cid + ")");
        }
        primitive.Int32.set(Variable.getPointer(varId), value);
    }

    public static long getInt64(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT64) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int64 (Class ID: " + cid + ")");
        }
        return primitive.Int64.get(Variable.getPointer(varId));
    }

    public static void setInt64(int varId, long value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT64) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int64 (Class ID: " + cid + ")");
        }
        primitive.Int64.set(Variable.getPointer(varId), value);
    }

    public static float getFloat32(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT32) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Float (Class ID: " + cid + ")");
        }
        return primitive.Float.get(Variable.getPointer(varId));
    }

    public static void setFloat32(int varId, float value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT32) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Float (Class ID: " + cid + ")");
        }
        primitive.Float.set(Variable.getPointer(varId), value);
    }

    public static double getFloat64(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT64) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Double (Class ID: " + cid + ")");
        }
        return primitive.Double.get(Variable.getPointer(varId));
    }

    public static void setFloat64(int varId, double value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT64) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Double (Class ID: " + cid + ")");
        }
        primitive.Double.set(Variable.getPointer(varId), value);
    }

    public static String getString(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_STRING) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type String (Class ID: " + cid + ")");
        }
        return primitive.string.get(Variable.getPointer(varId));
    }

    public static void setString(int varId, String value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_STRING) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type String (Class ID: " + cid + ")");
        }
        long oldPtr = Variable.getPointer(varId);
        if (oldPtr != 0L) {
            primitive.string.free(oldPtr);
        }
        long newPtr = primitive.string.allocate(value);
        Variable.setPointer(varId, newPtr);
    }

    public static boolean compareAndSetString(int varId, long expectedPtr, String newValue) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_STRING) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type String (Class ID: " + cid + ")");
        }
        long pointerAddr = Variable.getPointerAddress(varId);
        return primitive.string.compareAndSetString(pointerAddr, expectedPtr, newValue);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
