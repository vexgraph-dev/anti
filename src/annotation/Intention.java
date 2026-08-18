package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// when the annotation is being intended, must be annotated on top of header/class for a reason @annot(params)
// Source-only retention: stripped completely by javac at compile time (0 bytes in bytecode, 0 image heap footprint)
@Retention(RetentionPolicy.SOURCE)
public @interface Intention {
    String value() default "";
}
