package relational;

import annotation.Draft;
import annotation.Intention;
import annotation.Required;
import oop.TypeRegister;
import primitive.Int;
import primitive.Long;
import variable.Variable;

import nio.StringLookup;
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
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(17) + cid + StringLookup.getJavaString(18));
        }
        return Int.get(Variable.getPointer(varId));
    }

    public static void setInt(int varId, int value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_INT) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(17) + cid + StringLookup.getJavaString(18));
        }
        Int.set(Variable.getPointer(varId), value);
    }

    public static long getLong(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_LONG) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(19) + cid + StringLookup.getJavaString(18));
        }
        return Long.get(Variable.getPointer(varId));
    }

    public static void setLong(int varId, long value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_LONG) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(19) + cid + StringLookup.getJavaString(18));
        }
        Long.set(Variable.getPointer(varId), value);
    }

    public static float getFloat(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(20) + cid + StringLookup.getJavaString(18));
        }
        return primitive.Float.get(Variable.getPointer(varId));
    }

    public static void setFloat(int varId, float value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_FLOAT) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(20) + cid + StringLookup.getJavaString(18));
        }
        primitive.Float.set(Variable.getPointer(varId), value);
    }

    public static double getDouble(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_DOUBLE) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(21) + cid + StringLookup.getJavaString(18));
        }
        return primitive.Double.get(Variable.getPointer(varId));
    }

    public static void setDouble(int varId, double value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_DOUBLE) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(21) + cid + StringLookup.getJavaString(18));
        }
        primitive.Double.set(Variable.getPointer(varId), value);
    }

    public static String getString(int varId) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_STRING) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(22) + cid + StringLookup.getJavaString(18));
        }
        return primitive.string.get(Variable.getPointer(varId));
    }

    public static void setString(int varId, String value) {
        int cid = Variable.getClassId(varId);
        if (cid != TypeRegister.ID_STRING) {
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(22) + cid + StringLookup.getJavaString(18));
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
            throw new IllegalArgumentException(StringLookup.getJavaString(16) + varId + StringLookup.getJavaString(22) + cid + StringLookup.getJavaString(18));
        }
        long pointerAddr = Variable.getPointerAddress(varId);
        return primitive.string.compareAndSetString(pointerAddr, expectedPtr, newValue);
    }

    public static int classId() {
        return CLASS_ID;
    }
}
