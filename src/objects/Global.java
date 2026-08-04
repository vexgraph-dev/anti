package objects;

import annotation.Intention;

// global variable, this is where the variable is being associated with a global context...
public class Global
{

    // [purpose]
    // the purpose of the global variable is that the gloabel variable will be used to make
    // a pointer that is dedicated for a single variable (whether it be a struct, a primitive,
    // an array of objects, etc. it can act as just a pointer, it doesnt allocate a array for
    // each thread. shall be allocate as global when allocated for games.


    @Intention("[purpose] line [n]")
    private Global() {}
}
