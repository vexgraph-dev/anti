package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// invoke this annotation when the file/method is being drafted/written/generated as a first fresh write, not ready for production
@Retention(RetentionPolicy.CLASS)
public @interface Draft {}
