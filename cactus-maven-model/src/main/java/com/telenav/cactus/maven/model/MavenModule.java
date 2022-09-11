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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.util.Collections.emptySet;

/**
 * Represents a <code>&lt;module&gt;</code> entry in a Maven
 * <code>pom.xml</code> file.
 *
 * @author Tim Boudreau
 */
public final class MavenModule implements DiskResident, Comparable<MavenModule>
{
    private final String name;
    private final Pom owner;

    MavenModule(String name, Pom owner)
    {
        this.name = notNull("name", name);
        this.owner = notNull("owner", owner);
    }

    public MavenModule module(String name, Pom owner)
    {
        return new MavenModule(name, owner);
    }

    static Set<MavenModule> fromStrings(Pom in,
            Collection<? extends String> strings)
    {
        if (strings == null || strings.isEmpty())
        {
            return emptySet();
        }
        Set<MavenModule> result = new TreeSet<>();
        for (String mod : strings)
        {
            result.add(new MavenModule(mod, in));
        }
        return result;
    }
    
    public ThrowingOptional<Pom> toPom()
    {
        return Pom.from(pom());
    }

    public Pom owner()
    {
        return owner;
    }

    public String name()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return name();
    }

    @Override
    public Path path()
    {
        return owner.projectFolder().resolve(name);
    }

    public Path pom()
    {
        return path().resolve("pom.xml");
    }

    public boolean exists()
    {
        return Files.exists(pom());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || o.getClass() != MavenModule.class)
            {
                return false;
            }
        MavenModule other = (MavenModule) o;
        // Note we do not test on disk location, intentionally - we are
        // equal if we are the same name as a child of a parent with the
        // same group-id and artifact-id, so equality works across updates
        return name().equals(other.name())
                && other.owner().toArtifactIdentifiers().equals(owner
                        .toArtifactIdentifiers());
    }

    @Override
    public int hashCode()
    {
        return (name().hashCode() * 31) + 11 * owner.toArtifactIdentifiers()
                .hashCode();
    }

    @Override
    public int compareTo(MavenModule o)
    {
        return name().compareTo(o.name());
    }
}
