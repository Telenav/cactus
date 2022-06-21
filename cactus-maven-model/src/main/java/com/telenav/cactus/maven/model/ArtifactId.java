package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public final class ArtifactId extends ResolvablePomElement<ArtifactId>
{
    ArtifactId(Node node)
    {
        super(node);
    }

    ArtifactId(String artifactId)
    {
        super(artifactId);
    }

    public static ArtifactId of(Node what)
    {
        return new ArtifactId(what);
    }

    public static ArtifactId of(String what)
    {
        return new ArtifactId(notNull("what", what));
    }

    @Override
    ArtifactId newInstance(String what)
    {
        return of(what);
    }

}
