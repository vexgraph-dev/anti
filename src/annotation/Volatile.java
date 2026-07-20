package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// this annotation must be put where a function/class is a volatile class that every class being added will have to do with the modification of the framework
@Retention(RetentionPolicy.RUNTIME)
public @interface Volatile
{
}
