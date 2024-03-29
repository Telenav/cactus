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
package com.telenav.cactus.maven.model.resolver;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.property.ParentsPropertyResolver;
import com.telenav.cactus.maven.model.property.PropertyResolver;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A thing that can resolve pom files somehow, such as from among a collection
 * of known poms on disk, or from the local repository.
 *
 * @author Tim Boudreau
 */
public interface PomResolver
{

    /**
     * Get a PomResolver over the local maven repository.
     *
     * @return A PomResolver
     */
    static PomResolver local()
    {
        return LocalRepoResolver.INSTANCE;
    }

    /**
     * Combine this with the local maven repository.
     *
     * @return A PomResolver
     */
    default PomResolver withLocalRepository()
    {
        return or(local());
    }

    /**
     * Create a property resolver which can resolve properties within the passed
     * pom file or any of its parents that can be located.
     *
     * @param pom A pom file
     * @return A property resolver
     */
    default PropertyResolver propertyResolver(Pom pom)
    {
        return new ParentsPropertyResolver(pom, this);
    }

    /**
     * Returns a wrapper around this instance which caches results.
     *
     * @return A pom resolver
     */
    default PomResolver memoizing()
    {
        return new MemoizingPomResolver(this);
    }

    /**
     * Get the pom instance for an object that represents maven coordinates,
     * such as MavenCoordinates, Dependency, etc.
     *
     * @param <T>
     * @param obj
     * @return
     */
    default <T extends MavenIdentified & MavenVersioned> ThrowingOptional<Pom> get(
            T obj)
    {
        ThrowingOptional<String> ver = notNull("obj", obj).resolvedVersion();
        if (ver.isPresent())
        {
            return get(obj.groupId(), obj.artifactId(),
                    obj.version());
        }
        else
        {
            return get(obj.groupId().text(), obj.artifactId().text());
        }
    }

    default ThrowingOptional<Pom> get(GroupId groupId, ArtifactId artifactId)
    {
        return get(notNull("groupId", groupId).text(),
                notNull("artifactId", artifactId).text());
    }

    /**
     * Find a single artifact matching an ID. Implementations over the local
     * repository do not return a result for this, as it would mean walking the
     * entire tree.
     *
     * @param artifact An artifact ID
     * @return A pom, if possible. The default implementation returns empty(),
     * since this interface may be over a collection of poms in a project tree,
     * <i>or</i> over the entire local repository, which is both impractical to
     * scan and could easily result in a wrong result.
     */
    default ThrowingOptional<Pom> get(ArtifactId artifact)
    {
        return ThrowingOptional.empty();
    }

    /**
     * Get a Pom matching the passed group id and artifact id, if such exists.
     *
     * @param groupId A group id
     * @param artifactId An artifact id
     * @param version A version
     * @return An optional
     */
    default ThrowingOptional<Pom> get(GroupId groupId, ArtifactId artifactId,
            PomVersion version)
    {
        if (version.isPlaceholder())
        {
            return get(groupId.text(), artifactId.text());
        }
        return get(groupId.text(), artifactId.text(), version.text());
    }

    default ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        return get(groupId, artifactId).flatMapThrowing(pom
                -> version.equals(pom.coordinates().version.text())
                   ? ThrowingOptional.of(pom)
                   : ThrowingOptional.empty());
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId);

    default PomResolver or(PomResolver parent)
    {
        if (parent == this)
        {
            return this;
        }
        return new PomResolver()
        {
            @Override
            public ThrowingOptional<Pom> get(String groupId, String artifactId)
            {
                return PomResolver.this.get(groupId, artifactId)
                        .or(parent.get(groupId, artifactId));
            }

            @Override
            public ThrowingOptional<Pom> get(String groupId, String artifactId,
                    String version)
            {
                return PomResolver.this.get(groupId, artifactId, version)
                        .or(parent.get(groupId, artifactId, version));
            }

            @Override
            public ThrowingOptional<Pom> get(ArtifactId artifact)
            {
                return PomResolver.this.get(artifact)
                        .orThrowing(() -> parent.get(artifact));
            }

            @Override
            public PomResolver or(PomResolver with)
            {
                if (with == this || with == parent || with == PomResolver.this)
                {
                    return this;
                }
                return PomResolver.super.or(with);
            }

            @Override
            public String toString()
            {
                return PomResolver.this.toString() + " -> " + parent;
            }
        };
    }
}
