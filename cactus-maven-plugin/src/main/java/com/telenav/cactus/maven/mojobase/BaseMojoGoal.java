package com.telenav.cactus.maven.mojobase;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Maven's Mojo annotation is not runtime-retained, but we need access to the
 * name of the goal. So, for now at least, using a runtime annotation which must
 * be kept in sync with the Mojo annotation to accomplish that - several run
 * policies need to know the goal.
 *
 * @author Tim Boudreau
 */
@Retention(RUNTIME)
@Target(ElementType.TYPE)
public @interface BaseMojoGoal
{
    String value();
}
