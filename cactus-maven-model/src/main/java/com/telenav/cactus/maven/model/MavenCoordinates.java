package com.telenav.cactus.maven.model;

import org.w3c.dom.Node;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * @author Tim Boudreau
 */
public class MavenCoordinates implements Comparable<MavenCoordinates>
{
    static final String PLACEHOLDER = "---";

    public static Optional<MavenCoordinates> from(Path pomFile)
    {
        // Pending - wipeable cache?
        try
        {
            return Optional.of(new PomFile(pomFile).coordinates());
        }
        catch (Exception ex)
        {
            return Optional.empty();
        }
    }

    public final String groupId;

    public final String artifactId;

    public final String version;

    public MavenCoordinates(Node groupId, Node artifactId, Node version)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId),
                textOrPlaceholder(version));
    }

    public MavenCoordinates(String groupId, String artifactId, String version)
    {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public MavenCoordinates withVersion(String newVersion)
    {
        return new MavenCoordinates(groupId, artifactId, newVersion);
    }

    public Optional<String> version()
    {
        return PLACEHOLDER.equals(version)
               ? Optional.empty()
               : Optional.of(version);
    }

    public MavenCoordinates withResolvedVersion(Supplier<String> versionResolver)
    {
        if (PLACEHOLDER.equals(version))
        {
            String newVersion = versionResolver.get();
            if (newVersion != null)
            {
                return withVersion(newVersion);
            }
        }
        return this;
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

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != MavenCoordinates.class)
            {
                return false;
            }
        MavenCoordinates other = (MavenCoordinates) o;
        return version.equals(other.version) && artifactId.equals(
                other.artifactId)
                && groupId.equals(other.groupId);
    }

    @Override
    public int hashCode()
    {
        return (137 * artifactId.hashCode())
                + (3 * groupId.hashCode())
                + (11 * version.hashCode());
    }

    public boolean is(MavenCoordinates other)
    {
        return other.groupId.equals(groupId) && other.artifactId.equals(
                artifactId);
    }

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId + ":" + version;
    }

    static String textOrPlaceholder(Node node)
    {
        return node == null
               ? PLACEHOLDER
               : node.getTextContent().trim();
    }
}
