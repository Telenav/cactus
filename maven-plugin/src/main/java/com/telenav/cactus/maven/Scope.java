package com.telenav.cactus.maven;

import java.util.Arrays;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * A number of cross repository git operations can operate on one of several
 * scopes.
 *
 * @author Tim Boudreau
 */
public enum Scope
{
    /**
     * Operate only on the git submodule the project maven was invoked against
     * belongs to.
     */
    JUST_THIS,
    /**
     * Operate on all git submodules within the tree of the project maven was
     * invoked against that contains the same group id as the project maven was
     * invoked against.
     */
    FAMILY,
    /**
     * Operate on all git submodules containing a root pom.xml within any
     * submodule below the root of the project tree the project maven was
     * invoked against lives in.
     */
    ALL;

    public static Scope find(String prop) throws MojoExecutionException
    {
        if (prop == null)
        {
            return FAMILY;
        }
        for (Scope scope : Scope.values())
        {
            if (scope.name().equalsIgnoreCase(prop))
            {
                return scope;
            }
        }
        String msg = "Unknown scope " + prop + " is not one of " + Arrays.toString(Scope.values());
        throw new MojoExecutionException(Scope.class, msg, msg);
    }

}
