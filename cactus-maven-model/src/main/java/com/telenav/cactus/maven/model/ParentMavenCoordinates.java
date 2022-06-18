package com.telenav.cactus.maven.model;

import java.util.Optional;
import org.w3c.dom.Node;

/**
 *
 * @author Tim Boudreau
 */
public class ParentMavenCoordinates extends MavenCoordinates
{
    public final Optional<String> relativePath;

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
        this.relativePath = Optional.ofNullable(relativePath);
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
