package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Applied to framework-level classes that mutate engine state, modify core layout/memory contracts, or control thread dispatching.
@Retention(RetentionPolicy.RUNTIME)
public @interface Volatile
{
}
