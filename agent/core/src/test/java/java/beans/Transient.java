package java.beans;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * For testing purpose on Java 1.6
 * @author roland
 * @since 04.09.18
 */
@Target({METHOD})
@Retention(RUNTIME)
public @interface Transient {
    boolean value() default true;
}
