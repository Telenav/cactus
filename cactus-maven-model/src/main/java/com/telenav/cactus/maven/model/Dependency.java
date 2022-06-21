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
import static com.telenav.cactus.maven.model.MavenCoordinates.PLACEHOLDER;

/**
 * A single maven dependency.
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
    public final boolean implicitScope;
    public final boolean implicitType;

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
        implicitScope = scope == null;
        implicitType = type == null;
        this.optional = optional;
        this.exclusions = exclusions == null || exclusions.isEmpty()
                          ? Collections.emptySet()
                          : Collections.unmodifiableSet(
                        new HashSet<>(exclusions));
    }

    public Set<MavenId> exclusions()
    {
        return Collections.unmodifiableSet(exclusions);
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
        return PLACEHOLDER.equals(coords.version);
    }

    public Dependency withVersion(String version)
    {
        if (this.coords.version.equals(version))
        {
            return this;
        }
        MavenCoordinates newCoords = this.coords.withVersion(version);
        return new Dependency(newCoords, implicitType
                                         ? null
                                         : type, implicitScope
                                                 ? null
                                                 : scope, optional, exclusions);
    }

    public Dependency withType(String type)
    {
        if (this.type.equals(type))
        {
            return this;
        }
        return new Dependency(coords,
                type, implicitScope
                      ? null
                      : scope, optional, exclusions);
    }

    public Dependency withScope(DependencyScope scope)
    {
        if (scope == this.scope)
        {
            return this;
        }
        return new Dependency(coords, implicitType
                                      ? null
                                      : type, scope, optional, exclusions);
    }

    public Dependency withExclusions(Collection<? extends MavenId> excl)
    {
        if (exclusions.equals(this.exclusions))
        {
            return this;
        }
        return new Dependency(coords, implicitType
                                      ? null
                                      : type, implicitScope
                                              ? null
                                              : scope,
                optional, new HashSet<>(exclusions));
    }

    public Dependency withCombinedExclusions(
            Collection<? extends MavenId> exclusions)
    {
        if (exclusions == this.exclusions || exclusions.equals(this.exclusions))
        {
            return this;
        }
        Set<MavenId> result = new HashSet<>(exclusions);
        result.addAll(this.exclusions);
        return new Dependency(coords, implicitType
                                      ? null
                                      : type, implicitScope
                                              ? null
                                              : scope,
                optional, result);
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

    public MavenId toMavenId()
    {
        return coords.toMavenId();
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

    public boolean isOptional()
    {
        return optional;
    }

    public Dependency resolve(PropertyResolver res)
    {
        Dependency result = this;
        MavenCoordinates cds = coords;
        if (!PropertyResolver.isResolved(coords.groupId))
        {
            String gid = res.resolve(coords.groupId);
            if (gid != null && !gid.equals(coords.groupId))
            {
                cds = cds.withGroupId(gid);
            }
        }
        if (!PropertyResolver.isResolved(coords.version))
        {
            String ver = res.resolve(coords.version);
            if (ver != null && !ver.equals(coords.version))
            {
                cds = cds.withVersion(ver);
            }
        }
        if (cds != coords)
        {
            result = new Dependency(cds, implicitType
                                         ? null
                                         : type,
                    implicitScope
                    ? null
                    : scope, optional, exclusions);
        }
        return result;
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
}
