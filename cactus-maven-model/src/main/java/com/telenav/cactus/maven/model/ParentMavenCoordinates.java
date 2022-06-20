package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import java.nio.file.Path;
import org.w3c.dom.Node;

/**
 * Extension of MavenCoordinates which may have a relative path to a parent pom
 * or project.
 *
 * @author Tim Boudreau
 */
public final class ParentMavenCoordinates extends MavenCoordinates
{
    public final ThrowingOptional<String> relativePath;

    public ParentMavenCoordinates(Node groupId, Node artifactId, Node version,
            Node rp)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId),
                textOrPlaceholder(version), relativePathFrom(rp));
    }

    public ParentMavenCoordinates(String groupId, String artifactId,
            String version, String relativePath)
    {
        super(groupId, artifactId, version);
        this.relativePath = ThrowingOptional.ofNullable(relativePath);
    }
    
    public MavenCoordinates toPlainMavenCoordinates() {
        return new MavenCoordinates(groupId(), artifactId(), version);
    }

    public ThrowingOptional<Path> relativePath(Path to)
    {
        return relativePath.map(p -> to.resolve(p));
    }

    @Override
    public ParentMavenCoordinates withVersion(String newVersion)
    {
        return new ParentMavenCoordinates(groupId, artifactId, newVersion,
                relativePath.isPresent()
                ? relativePath.get()
                : null);
    }

    private static String relativePathFrom(Node n)
    {
        if (n == null)
        {
            return "..";
        }
        if (n.getTextContent() == null || n.getTextContent().isBlank())
        {
            return null;
        }
        return n.getTextContent().trim();
    }

}
