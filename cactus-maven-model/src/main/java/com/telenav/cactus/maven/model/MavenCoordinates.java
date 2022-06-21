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
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * @author Tim Boudreau
 */
public class MavenCoordinates extends ArtifactIdentifiers implements
        Comparable<MavenCoordinates>,
        MavenIdentified, MavenVersioned
{
    public final PomVersion version;

    public MavenCoordinates(Node groupId, Node artifactId, Node version)
    {
        this(GroupId.of(groupId), ArtifactId.of(artifactId),
                PomVersion.of(version));
    }

    public MavenCoordinates(GroupId groupId, ArtifactId artifactId,
            PomVersion version)
    {
        super(notNull("groupId", groupId), notNull("artifactId", artifactId));
        this.version = notNull("version", version);
    }

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

    /**
     * Apply a new version returning a new instance if it differs.
     * 
     * @param newVersion The new version
     * @return A maven coordinates
     */
    public MavenCoordinates withVersion(String newVersion)
    {
        if (version.is(newVersion))
        {
            return this;
        }
        return new MavenCoordinates(groupId, artifactId, PomVersion.of(
                newVersion));
    }
    
    public PomVersion rawVersion() {
        return version;
    }

    /**
     * Apply a new version returning a new instance if it differs.
     * 
     * @param newVersion The new version
     * @return A maven coordinates
     */
    public MavenCoordinates withVersion(PomVersion newVersion)
    {
        if (version.equals(newVersion))
        {
            return this;
        }
        return new MavenCoordinates(groupId, artifactId, newVersion);
    }

    public MavenCoordinates withGroupId(String newGroupId)
    {
        if (groupId.is(newGroupId))
        {
            return this;
        }
        return new MavenCoordinates(GroupId.of(newGroupId), artifactId,
                version);
    }

    public ArtifactIdentifiers toMavenId()
    {
        return new ArtifactIdentifiers(groupId(), artifactId());
    }

    @Override
    public GroupId groupId()
    {
        return groupId;
    }

    @Override
    public ArtifactId artifactId()
    {
        return artifactId;
    }

    public MavenCoordinates toPlainMavenCoordinates()
    {
        return this;
    }

    @Override
    public boolean isResolved()
    {
        return version.isResolved() && groupId.isResolved() && artifactId
                .isResolved();
    }

    public MavenCoordinates resolve(Function<String, String> res)
    {
        GroupId gid = groupId.resolve(res);
        ArtifactId aid = artifactId.resolve(res);
        PomVersion ver = version.resolve(res);
        if (gid != groupId || aid != artifactId || ver != version)
        {
            MavenCoordinates nue = new MavenCoordinates(gid, aid, ver);
            return nue;
        }
        return this;
    }

    public MavenCoordinates resolve(PropertyResolver res, PomResolver poms)
    {
        PomVersion ver = version;
        if (ver.isPlaceholder())
        {
            ver = poms.get(groupId.value(), artifactId.value())
                    .map(pom ->
                    {
                        return pom.coords.version;
                    }).orElse(version);
        }
        MavenCoordinates nue = ver == version
                               ? this
                               : new MavenCoordinates(groupId, artifactId, ver);
        return nue.resolve(res);
    }

    @Override
    public ThrowingOptional<String> version()
    {
        return version.ifResolved();
    }

    @Override
    public int compareTo(MavenCoordinates o)
    {
        return groupId.compare(o.groupId,
                () -> artifactId.compare(o.artifactId,
                        () -> version.compareTo(o.version)));
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
}
