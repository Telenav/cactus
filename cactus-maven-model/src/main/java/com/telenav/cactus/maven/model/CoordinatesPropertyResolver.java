package com.telenav.cactus.maven.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
class CoordinatesPropertyResolver extends AbstractPropertyResolver
{
    private final List<String> keys = new ArrayList<>(Arrays.asList(
            "project.groupId", "project.artifactId", "project.version"));
    private final MavenCoordinates self;
    private final MavenCoordinates parent;

    CoordinatesPropertyResolver(MavenCoordinates self, MavenCoordinates parent)
    {
        this.self = notNull("self", self);
        this.parent = parent; // can be null
        if ("project.groupId".equals(self.groupId))
        {
            throw new IllegalStateException(self + "");
        }
        if (parent != null && "project.groupId".equals(parent.groupId))
        {
            throw new IllegalStateException(parent + "");
        }
        if (parent != null)
        {
            keys.add("project.parent.groupId");
            keys.add("parent.groupId");
            keys.add("project.parent.artifactId");
            keys.add("parent.artifactId");
            keys.add("project.parent.version");
            keys.add("parent.version");
        }
    }

    CoordinatesPropertyResolver(Pom pom)
    {
        this(pom.coords, pom.parent().orElse(null));
    }


    @Override
    public String toString()
    {
        return "Coordinates(" + self + ", " + parent + ")";
    }

    @Override
    protected String valueFor(String k)
    {
        if (k.startsWith("${")) {
            new RuntimeException(k).printStackTrace();
        }
        if (parent != null)
        {
            switch (k)
            {
                case "project.parent.groupId":
                case "parent.groupId":
                    return parent.groupId;
                case "project.parent.artifactId":
                case "parent.artifactId":
                    return parent.artifactId;
                case "project.parent.version":
                case "parent.version":
                    return parent.version;
            }
        }
        switch (k)
        {
            case "project.groupId":
                return self.groupId;
            case "project.version":
                if (self.version().isPresent())
                {
                    return self.version().get();
                }
            case "project.artifactId":
                return self.artifactId;
        }
        return null;
    }

    @Override
    public Iterator<String> iterator()
    {
        return keys.iterator();
    }

}
