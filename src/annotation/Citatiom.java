package annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// we are citing things to the CITATION.md
// the IEEE way as well...
@Retention(RetentionPolicy.SOURCE)
public @interface Citatiom
{
    int cite();
}
