////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.dependencies.DependencyScope;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.model.Packaging.packaging;
import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 * A single maven dependency.
 *
 * @author Tim Boudreau
 */
public class Dependency implements MavenArtifactCoordinates
{

    private final MavenCoordinates coords;
    private final Packaging type;
    private final DependencyScope scope;
    private final boolean optional;
    private final Set<ArtifactIdentifiers> exclusions;
    private final boolean implicitScope;
    private final boolean implicitType;

    public Dependency(MavenCoordinates coords, String type, String scope,
            boolean optional, Set<ArtifactIdentifiers> exclusions)
    {
        this(coords, type, DependencyScope.of(scope), optional, exclusions);
    }

    public Dependency(MavenCoordinates coords, String type,
            DependencyScope scope,
            boolean optional, Set<ArtifactIdentifiers> exclusions)
    {
        this.coords = notNull("coords", coords);
        this.type = type == null
                    ? Packaging.JAR
                    : packaging(type);
        this.scope = scope == null
                     ? DependencyScope.Compile
                     : scope;
        implicitScope = scope == null;
        implicitType = type == null;
        this.optional = optional;
        this.exclusions = exclusions == null || exclusions.isEmpty()
                          ? Collections.emptySet()
                          : Collections.unmodifiableSet(
                        new HashSet<>(exclusions));
    }

    private Dependency(MavenCoordinates coords, Packaging type,
            boolean implicitType,
            DependencyScope scope, boolean implicitScope,
            Set<ArtifactIdentifiers> exclusions,
            boolean optional
    )
    {
        this.coords = notNull("coords", coords);
        this.type = notNull("type", type);
        this.scope = notNull("scope", scope);
        this.exclusions = exclusions.isEmpty()
                          ? exclusions
                          : unmodifiableSet(exclusions);
        this.implicitScope = implicitScope;
        this.implicitType = implicitType;
        this.optional = optional;
    }

    public Set<ArtifactIdentifiers> exclusions()
    {
        return exclusions;
    }

    public boolean excludes(MavenIdentified artifact)
    {
        notNull("artifact", artifact);
        for (ArtifactIdentifiers ids : exclusions)
        {
            if (ids.is(artifact))
            {
                return true;
            }
        }
        return false;
    }

    public boolean isImplictScope()
    {
        return implicitScope;
    }

    public boolean isImplicitType()
    {
        return implicitType;
    }

    public boolean isPlaceholderVersion()
    {
        return coords.version.isPlaceholder();
    }

    public Dependency withVersion(PomVersion version)
    {
        if (this.coords.version.equals(notNull("version", version)))
        {
            return this;
        }
        MavenCoordinates newCoords = this.coords.withVersion(version);
        return new Dependency(newCoords, implicitType
                                         ? null
                                         : type.toString(), implicitScope
                                                            ? null
                                                            : scope, optional,
                exclusions);
    }

    public Dependency withVersion(String version)
    {
        if (this.coords.version.is(version))
        {
            return this;
        }
        MavenCoordinates newCoords = this.coords.withVersion(version);
        return new Dependency(newCoords, type, implicitType,
                scope, implicitScope, exclusions, optional);
    }

    public Dependency withType(String type)
    {
        if (this.type.is(type))
        {
            return this;
        }
        return new Dependency(coords,
                type, implicitScope
                      ? null
                      : scope, optional, exclusions);
    }

    public Dependency withType(Packaging type)
    {
        if (this.type.equals(type))
        {
            return this;
        }
        return new Dependency(coords, type, false, scope, implicitScope,
                exclusions, optional);
    }

    public Dependency withScope(DependencyScope scope)
    {
        if (scope == this.scope)
        {
            return this;
        }
        return new Dependency(coords, type, implicitType, scope,
                implicitScope, exclusions, optional);
    }

    public Dependency withExclusions(
            Collection<? extends ArtifactIdentifiers> excl)
    {
        if (exclusions.equals(this.exclusions))
        {
            return this;
        }
        return new Dependency(coords, type, implicitType, scope,
                implicitScope, exclusions.isEmpty()
                               ? emptySet()
                               : new HashSet<>(exclusions), optional);
    }

    public Dependency withCombinedExclusions(
            Collection<? extends ArtifactIdentifiers> exclusions)
    {
        if (exclusions == this.exclusions
                || exclusions.equals(this.exclusions)
                || exclusions.isEmpty())
        {
            return this;
        }
        Set<ArtifactIdentifiers> result = new HashSet<>(exclusions);
        result.addAll(this.exclusions);
        return new Dependency(coords, type, implicitType, scope, implicitScope,
                result, optional);
    }

    public boolean isCompletionOf(Dependency other)
    {
        if (!isResolved())
        {
            return false;
        }
        if (artifactId().equals(other.artifactId()) && groupId().equals(other
                .groupId()))
        {
            if (type.equals(other.type) || other.implicitType)
            {
                return true;
            }
        }
        return false;
    }

    public ArtifactIdentifiers toMavenId()
    {
        return coords.toMavenId();
    }

    @Override
    public GroupId groupId()
    {
        return coords.groupId();
    }

    @Override
    public ArtifactId artifactId()
    {
        return coords.artifactId();
    }

    @Override
    public PomVersion version()
    {
        return coords.version();
    }

    /**
     * Get the version as a string, if and only if it is fully resolved (not a
     * placeholder or a property reference).
     *
     * @return An optional
     */
    @Override
    public ThrowingOptional<String> resolvedVersion()
    {
        return coords.resolvedVersion();
    }

    /**
     * Determine if this dependency is free of <code>${property}</code> values
     * and/or placeholder markers.
     *
     * @return True if this pom is resolved
     */
    @Override
    public boolean isResolved()
    {
        return coords.isResolved();
    }

    /**
     * Determine if this dependency was marked as optional in its originating
     * pom.
     *
     * @return True if this dependency is optional
     */
    public boolean isOptional()
    {
        return optional;
    }

    /**
     * Resolve any <code>${property}</code> elements in this dependency, using
     * the passed property resolver.
     *
     * @param res A property resolver.
     * @return A new dependency or this.
     */
    public Dependency resolve(PropertyResolver res)
    {
        MavenCoordinates cds = coords.resolve(res);
        if (cds != coords)
        {
            return new Dependency(cds, type, implicitType,
                    scope, implicitScope, exclusions, optional);
        }
        return this;
    }

    /**
     * Return a dependency, resolving coordinates against the passed pom
     * resolver and poms, and returning a new instance if anything has changed.
     *
     * @param res A property resolver
     * @param poms A set of poms or other mechanism for resolving the dependency
     * to a concrete version, group id, etc.
     * @return A new dependency or this
     */
    public Dependency resolve(PropertyResolver res, PomResolver poms)
    {
        MavenCoordinates result = coords.resolve(res, poms);
        if (result != coords)
        {
            return new Dependency(result, type, implicitType,
                    scope, implicitScope, exclusions, optional);
        }
        return this;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 11 * hash + Objects.hashCode(this.coords);
        hash = 11 * hash + Objects.hashCode(this.type);
        hash = 11 * hash + this.scope.ordinal();
        hash = 11 * hash + (this.optional
                            ? 1
                            : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null || obj.getClass() != Dependency.class)
        {
            return false;
        }
        final Dependency other = (Dependency) obj;
        if (this.optional != other.optional)
        {
            return false;
        }
        if (!Objects.equals(this.type, other.type))
        {
            return false;
        }
        if (!Objects.equals(this.scope, other.scope))
        {
            return false;
        }
        return Objects.equals(this.coords, other.coords);
    }

    @Override
    public String toString()
    {
        return type + "(" + coords + "=" + scope
                + (exclusions.isEmpty()
                   ? ""
                   : " excluding="
                + exclusions.toString()
                + (optional
                   ? " optional"
                   : "")) + ")";
    }

    /**
     * @return the coords
     */
    public MavenCoordinates coordinates()
    {
        return coords;
    }

    /**
     * @return the type
     */
    public Packaging type()
    {
        return type;
    }

    /**
     * @return the scope
     */
    public DependencyScope scope()
    {
        return scope;
    }

    /**
     * @return the implicitScope
     */
    public boolean isImplicitScope()
    {
        return implicitScope;
    }
}
