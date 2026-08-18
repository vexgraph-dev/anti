package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// invoke this annotation when the file/method is incomplete
// (e.g. empty, missing implementation, untested)
// remove it once the function is fully implemented and tested.
@Retention(RetentionPolicy.SOURCE)
public @interface Incomplete {}
