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

import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Aggregates a group id and artifact id to make an object which identifies a
 * maven library, but not its version - often used for mapping references with
 * unknown versions to ones that have them.
 *
 * @author Tim Boudreau
 */
public class ArtifactIdentifiers implements MavenIdentified
{
    public final GroupId groupId;

    public final ArtifactId artifactId;

    public ArtifactIdentifiers(GroupId groupId, ArtifactId artifactId)
    {
        this.groupId = notNull("groupId", groupId);
        this.artifactId = notNull("artifactId", artifactId);
    }

    public ArtifactIdentifiers(Node groupId, Node artifactId)
    {
        this(GroupId.of(groupId), ArtifactId.of(artifactId));
    }
    
    public ArtifactIdentifiers(String gid, String aid) {
        this(GroupId.of(gid), ArtifactId.of(aid));
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

    @Override
    public String toString()
    {
        return groupId + ":" + artifactId;
    }

    @Override
    public int hashCode()
    {
        return 2 + (13 * artifactId.hashCode()) + (31 * groupId.hashCode());
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || !(o instanceof ArtifactIdentifiers))
            {
                return false;
            }
        ArtifactIdentifiers m = (ArtifactIdentifiers) o;
        return is(m);
    }
}
