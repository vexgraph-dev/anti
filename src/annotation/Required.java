package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// need to be put as required when the parts are needed to be put on every class (e.g. every class should be part of one method, etc.)
@Retention(RetentionPolicy.RUNTIME)
public @interface Required
{}
