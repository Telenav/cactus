package com.telenav.cactus.maven.model;

import com.telenav.cactus.maven.model.property.PropertyResolver;
import java.util.function.Function;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public class MavenVersion
{

    private static final String UNKNOWN_VERSION = "---";
    /**
     * The unknown version, denoted by the string "---", which is used for
     * dependencies that are initially read from a &lt;dependencies&gt; section
     * of a pom, where the value will be determined by a
     * &lt;dependencyManagement&gt; section somewhere else.
     */
    public static final MavenVersion UNKNOWN = new MavenVersion(UNKNOWN_VERSION);
    private final String version;

    MavenVersion(String version)
    {
        this.version = notNull("version", version);
    }

    public static MavenVersion of(String what)
    {
        return new MavenVersion(what);
    }

    public MavenVersion resolve(Function<String, String> transform)
    {
        if (!isResolved())
        {
            String v = transform.apply(version);
            if (v != null && !version.equals(v))
            {
                return of(v);
            }
        }
        return this;
    }

    public boolean isUnknown()
    {
        return UNKNOWN_VERSION.equals(version);
    }

    public boolean isProperty()
    {
        return !PropertyResolver.isResolved(version);
    }

    public boolean isResolved()
    {
        return !isUnknown() && !isProperty();
    }

}
