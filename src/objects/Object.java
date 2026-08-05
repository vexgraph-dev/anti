package objects;

import annotation.Intention;
import annotation.Required;
import lang.Class;
import nio.ForeignMemory;
import oop.TypeRegister;

import java.util.Objects;

// [definition]
// the whole thing, this is the struct where it considers an object of any type, but still an object.
// id must be 0xAA000000 regardless (catch for all object and primitive)
@Intention("[definition]")
public class Object
{
    // it should have a uh, way that everything will be an object, so i guess this is the part where it checks if its an object and not a primitive
    @Required
    public static final int CATCH_ALL_ID = 0xAA000000;

    public static void free(long ptr)
    {
        ForeignMemory.freeNative(ptr - 8L);
    }

    public static int type(long ptr) {
        return Class.getType(ptr);
    }

    public static int length(long ptr) {
        if (ptr == 0L) return 0;
        return ForeignMemory.getUnsafeInt(ptr - 4L);
    }

    public static int classId(long ptr) {
        if (ptr == 0L) return 0;
        return TypeRegister.getClassId(type(ptr));
    }

    public static boolean isObject(long ptr) {
        if (ptr == 0L) return false;
        int t = type(ptr);
        return TypeRegister.isStruct(t) || TypeRegister.isPrimitive(t);
    }

    public static boolean isStruct(long ptr) {
        if (ptr == 0L) return false;
        return TypeRegister.isStruct(type(ptr));
    }

    public static boolean isPrimitive(long ptr) {
        if (ptr == 0L) return false;
        return TypeRegister.isPrimitive(type(ptr));
    }

    // [process]
    // im still confused on how we can make it viable to support
    // objects on a data structure, BUT if thats the case, the obejct
    // shall be putting a memory layout that goes like this:
    //
    // in a data structure:
    // [metadata, its objects waow][object1][object2][object3]
    //
    // inside object:
    // [object id][field1][field2][field3][and so on, depending on each object]
    // the object id can already know what amount of stride it is, which field shall get,
    // what data is one and whatnot, etc.
    //
    // theres drawbacks of course, but this is one way of implementing oop without restricting
    // the outside user.
    @Intention("[process]")
    private Object() {}

}

