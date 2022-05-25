package com.telenav.cactus.maven.xml;

import java.nio.file.Path;
import java.util.Optional;
import org.w3c.dom.Node;

/**
 *
 * @author Tim Boudreau
 */
public final class MavenCoordinates implements Comparable<MavenCoordinates>
{

    public final String groupId;
    public final String artifactId;
    public final String version;

    public MavenCoordinates(Node groupId, Node artifactId, Node version)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId), textOrPlaceholder(version));
    }

    private static String textOrPlaceholder(Node node)
    {
        return node == null ? "---" : node.getTextContent().trim();
    }

    public MavenCoordinates(String groupId, String artifactId, String version)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public static Optional<MavenCoordinates> from(Path pomFile)
    {
        // Pending - wipeable cache?
        try
        {
            return Optional.of(new PomFile(pomFile).coordinates());
        } catch (Exception ex)
        {
            return Optional.empty();
        }
    }

    public boolean is(MavenCoordinates other)
    {
        return other.groupId.equals(groupId) && other.artifactId.equals(artifactId);
    }

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId + ":" + version;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        } else if (o == null || o.getClass() != MavenCoordinates.class)
        {
            return false;
        }
        MavenCoordinates other = (MavenCoordinates) o;
        return version.equals(other.version) && artifactId.equals(other.artifactId)
                && groupId.equals(other.groupId);
    }

    @Override
    public int hashCode()
    {
        return (137 * artifactId.hashCode())
                + (3 * groupId.hashCode())
                + (11 * version.hashCode());
    }

    @Override
    public int compareTo(MavenCoordinates o)
    {
        int result = groupId.compareTo(o.groupId);
        if (result == 0)
        {
            result = artifactId.compareTo(o.artifactId);
        }
        if (result == 0)
        {
            result = version.compareTo(o.version);
        }
        return result;
    }
}
