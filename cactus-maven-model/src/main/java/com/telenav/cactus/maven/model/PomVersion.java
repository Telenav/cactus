package com.telenav.cactus.maven.model;

import com.telenav.cactus.maven.model.resolver.versions.VersionComparator;
import org.w3c.dom.Node;


/**
 * A maven version which may be a placeholder, a property reference or a fully
 * specified version.
 *
 * @author Tim Boudreau
 */
public final class PomVersion extends ResolvablePomElement<PomVersion>
{

    /**
     * The unknown version, denoted by the string "---", which is used for
     * dependencies that are initially read from a &lt;dependencies&gt; section
     * of a pom, where the value will be determined by a
     * &lt;dependencyManagement&gt; section somewhere else.
     */
    public static final PomVersion UNKNOWN = new PomVersion(PLACEHOLDER);

    PomVersion(String version)
    {
        super(version);
    }

    PomVersion(Node node)
    {
        super(node);
    }

    public static PomVersion of(Node n)
    {
        if (n == null)
        {
            return UNKNOWN;
        }
        return new PomVersion(n);
    }

    public static PomVersion of(String what)
    {
        return new PomVersion(what);
    }

    @Override
    PomVersion newInstance(String what)
    {
        return of(what);
    }

    @Override
    public int compareTo(PomVersion o)
    {
        if (o.equals(this))
        {
            return 0;
        }
        return VersionComparator.INSTANCE.compare(this.text(), o.text());
    }

}
