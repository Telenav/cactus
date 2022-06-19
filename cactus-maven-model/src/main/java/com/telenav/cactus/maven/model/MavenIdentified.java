package com.telenav.cactus.maven.model;

import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public interface MavenIdentified
{

    String groupId();

    String artifactId();

    default boolean is(MavenIdentified other)
    {
        return other == this || (Objects.equals(groupId(), other.groupId())
                && Objects.equals(artifactId(), other.artifactId()));
    }
}
