package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public class Dependency implements MavenIdentified, MavenVersioned
{

    public final MavenCoordinates coords;
    public final String type;
    public final DependencyScope scope;
    public final boolean optional;
    public final Set<MavenId> exclusions;

    public Dependency(MavenCoordinates coords, String type, String scope,
            boolean optional, Set<MavenId> exclusions)
    {
        this(coords, type, DependencyScope.of(scope), optional, exclusions);
    }

    public Dependency(MavenCoordinates coords, String type,
            DependencyScope scope,
            boolean optional, Set<MavenId> exclusions)
    {
        this.coords = notNull("coords", coords);
        this.type = type == null
                    ? "jar"
                    : type;
        this.scope = scope == null
                     ? DependencyScope.Compile
                     : scope;
        this.optional = optional;
        this.exclusions = exclusions == null || exclusions.isEmpty()
                          ? Collections.emptySet()
                          : Collections.unmodifiableSet(
                        new HashSet<>(exclusions));
    }

    public MavenId toMavenId()
    {
        return coords.toMavenId();
    }

//    static List<Dependency> combine(List<Dependency> a, List<Dependency> b) {
//        
//    }
    public Optional<Dependency> merge(Dependency other)
    {
        if (!other.coords.is(coords))
        {
            return Optional.empty();
        }
        if (!other.type.equals(type))
        {
            return Optional.empty();
        }
        Set<MavenId> isect = new HashSet<>(other.exclusions);
        isect.retainAll(exclusions);
        boolean opt = optional && other.optional;
        DependencyScope sc = scope.coalesce(other.scope);
        return Optional.of(new Dependency(coords, type, sc, opt, isect));
    }

    public boolean excludes(MavenIdentified id)
    {
        if (exclusions.isEmpty())
        {
            return false;
        }
        if (id instanceof MavenId)
        {
            return exclusions.contains(id);
        }
        for (MavenId mid : exclusions)
        {
            if (mid.is(id))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String groupId()
    {
        return coords.groupId;
    }

    @Override
    public String artifactId()
    {
        return coords.artifactId;
    }

    @Override
    public ThrowingOptional<String> version()
    {
        return coords.version();
    }

    @Override
    public boolean isResolved()
    {
        return coords.isResolved();
    }

    public Dependency resolve(PropertyResolver res, PomResolver poms)
    {
        MavenCoordinates result = coords.resolve(res, poms);
        if (result != coords)
        {
            return new Dependency(result, type, scope, optional, exclusions);
        }
        return this;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.coords);
        hash = 11 * hash + Objects.hashCode(this.type);
        hash = 11 * hash + Objects.hashCode(this.scope);
        hash = 11 * hash + (this.optional
                            ? 1
                            : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final Dependency other = (Dependency) obj;
        if (this.optional != other.optional)
            return false;
        if (!Objects.equals(this.type, other.type))
            return false;
        if (!Objects.equals(this.scope, other.scope))
            return false;
        return Objects.equals(this.coords, other.coords);
    }

    @Override
    public String toString()
    {
        return type + "(" + coords + "=" + scope + (exclusions.isEmpty()
                                                    ? ""
                                                    : " excluding="
                + exclusions.toString()
                + (optional
                   ? " optional"
                   : "") + ")");
    }
}
