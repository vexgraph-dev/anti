package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// when the annotation is being intended, must be annotated on top of header/class for a reason @annot(params)
@Retention(RetentionPolicy.RUNTIME)
public @interface Intention {
    String value() default "";
}
