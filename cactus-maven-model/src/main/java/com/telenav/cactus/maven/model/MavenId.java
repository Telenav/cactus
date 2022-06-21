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

import static com.telenav.cactus.maven.model.MavenCoordinates.PLACEHOLDER;

/**
 *
 * @author Tim Boudreau
 */
public class MavenId implements MavenIdentified
{
    public final String groupId;

    public final String artifactId;

    public MavenId(String groupId, String artifactId)
    {
        if ("project.groupId".equals(groupId)) {
            throw new IllegalArgumentException("Using raw prop for " + artifactId);
        }
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    public MavenId(Node groupId, Node artifactId)
    {
        this(textOrPlaceholder(groupId), textOrPlaceholder(artifactId));
    }

    static String textOrPlaceholder(Node node)
    {
        return node == null
               ? PLACEHOLDER
               : node.getTextContent().trim();
    }

    @Override
    public String groupId()
    {
        return groupId;
    }

    @Override
    public String artifactId()
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
            if (o == null || !(o instanceof MavenId))
            {
                return false;
            }
        MavenId m = (MavenId) o;
        return is(m);
    }
}
