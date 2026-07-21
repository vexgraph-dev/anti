package relational;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.Int;
import primitive.Long;

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
    public static int getInt(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int (Class ID: " + cid + ")");
        }
        return Int.get(Variable.getPointer(varId));
    }

    public static void setInt(int varId, int value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Int (Class ID: " + cid + ")");
        }
        Int.set(Variable.getPointer(varId), value);
    }

    public static long getLong(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_LONG) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Long (Class ID: " + cid + ")");
        }
        return Long.get(Variable.getPointer(varId));
    }

    public static void setLong(int varId, long value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_LONG) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Long (Class ID: " + cid + ")");
        }
        Long.set(Variable.getPointer(varId), value);
    }

    public static float getFloat(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Float (Class ID: " + cid + ")");
        }
        return primitive.Float.get(Variable.getPointer(varId));
    }

    public static void setFloat(int varId, float value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Float (Class ID: " + cid + ")");
        }
        primitive.Float.set(Variable.getPointer(varId), value);
    }

    public static double getDouble(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_DOUBLE) {
            throw new IllegalArgumentException("Variable ID " + varId + " is not of type Double (Class ID: " + cid + ")");
        }
        return primitive.Double.get(Variable.getPointer(varId));
    }

    public static void setDouble(int varId, double value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_DOUBLE) {
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
