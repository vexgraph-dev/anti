package objects;

import annotation.Intention;

// variable where its just local, when theres another thread that uses, it should allocate a variable to act as an actual variable to be used in a local context
public class Locale
{
    // [purpose]
    // the purpose of the local variable is that the local variable will be used
    // to generate a bunch of singletons/pointers, regarding objects to act as a singleton
    // multipurpose variable. this is like when a user makes a custom variable in the script
    // and the same script is being run on multiple threads, that creates a mess. it shall be
    // managed accordingly based on their own. global on the other hand will create their own
    // race conditions over a SINGLE variable. if that makes even sense...

    @Intention("[purpose] line [n]")
    public Locale()
    {

    }
}
