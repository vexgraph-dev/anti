package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Source-only retention: stripped completely by javac at compile time (0 bytes in bytecode, 0 image heap footprint)
@Retention(RetentionPolicy.SOURCE)
public @interface PlatformExclusive
{
    String value();
}
