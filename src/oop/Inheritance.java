package oop;

import annotation.HotCode;
import annotation.Volatile;

import annotation.Draft;
import annotation.Intention;

@Draft
@Intention("Draft marker and subtype tree inspector for off-heap class hierarchy and polymorphism dispatch")
@Volatile
public class Inheritance {

    private Inheritance() {}

    @HotCode
    @Intention("subclass of two things, kinda instanceof type thing...")
    public static boolean isSubclassOf(int subClassId, int parentClassId) {
        if (subClassId == parentClassId)
            return true;
        int parent = TypeRegister.getParentClass(subClassId);
        if (parent != subClassId)
            return isSubclassOf(parent, parentClassId);
        return false;
    }
}

