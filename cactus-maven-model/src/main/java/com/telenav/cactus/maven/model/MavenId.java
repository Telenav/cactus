package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

import static com.telenav.cactus.maven.model.MavenCoordinates.PLACEHOLDER;

/**
 *
 * @author Tim Boudreau
 */
public class MavenId implements MavenIdentified
{
    public final String groupId;

    public final String artifactId;

    public MavenId(String groupId, String artifactId)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public MavenId(Node groupId, Node artifactId)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId));
    }

    static String textOrPlaceholder(Node node)
    {
        return node == null
               ? PLACEHOLDER
               : node.getTextContent().trim();
    }

    @Override
    public String groupId()
    {
        return groupId;
    }

    @Override
    public String artifactId()
    {
        return artifactId;
    }

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId;
    }

    @Override
    public int hashCode()
    {
        return 2 + (13 * artifactId.hashCode()) + (31 * groupId.hashCode());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || !(o instanceof MavenId))
            {
                return false;
            }
        MavenId m = (MavenId) o;
        return is(m);
    }
}
