package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public final class GroupId extends ResolvablePomElement<GroupId>
{
    GroupId(String groupId)
    {
        super(groupId);
    }

    GroupId(Node node)
    {
        super(node);
    }

    public static GroupId of(String what)
    {
        return new GroupId(notNull("what", what));
    }

    public static GroupId of(Node what)
    {
        return new GroupId(what);
    }

    @Override
    GroupId newInstance(String what)
    {
        return of(what);
    }

    /**
     * Compute a set of artifact identifiers by adding the passed artifact id
     * string to this group id.
     *
     * @param artifactId An artifact id
     * @return An ArtifactIdentifiers
     */
    public ArtifactIdentifiers artifact(String artifactId)
    {
        return artifact(ArtifactId.of(artifactId));
    }

    /**
     * Compute a set of artifact identifiers by adding the passed artifact id to
     * this group id.
     *
     * @param artifactId An artifact id
     * @return An ArtifactIdentifiers
     */
    public ArtifactIdentifiers artifact(ArtifactId id)
    {
        return new ArtifactIdentifiers(this, id);
    }
}
