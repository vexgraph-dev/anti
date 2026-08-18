package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Applied to classes or functions that require modification or updates every time a new class is added or registered in the framework.
@Retention(RetentionPolicy.SOURCE)
public @interface HotCode
{
}
