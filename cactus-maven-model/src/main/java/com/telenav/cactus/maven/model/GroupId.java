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
import java.util.Arrays;
import java.util.List;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A maven group id.
 *
 * @author Tim Boudreau
 */
public final class GroupId extends ResolvablePomElement<GroupId>
{
    GroupId(String groupId)
    {
        super(groupId);
    }

    GroupId(Node node)
    {
        super(node);
    }

    public static GroupId of(String what)
    {
        return new GroupId(notNull("what", what));
    }

    public static GroupId of(Node what)
    {
        return new GroupId(what);
    }

    /**
     * Get the group id as a list of those string segments delimited by
     * <code>.</code> characters.
     *
     * @return A list of strings
     */
    public List<String> segments()
    {
        return Arrays.asList(text().split("\\."));
    }

    /**
     * Concatenate another dot-delimited segment onto this group id.
     *
     * @param tail The tail segment.
     * @return A group id
     */
    public GroupId childGroupId(String tail)
    {
        return of(text() + "." + notNull("tail", tail));
    }

    /**
     * Get a group id omitting the last dot-delimted segment of this group id;
     * if no dots, returns empty().
     *
     * @return an optional
     */
    public ThrowingOptional<GroupId> parentGroupId()
    {
        String txt = text();
        int ix = txt.lastIndexOf('.');
        if (ix > 0 && ix < txt.length() - 1)
        {
            return ThrowingOptional.of(of(txt.substring(0, ix)));
        }
        return ThrowingOptional.empty();
    }

    @Override
    protected GroupId newInstance(String what)
    {
        return of(what);
    }

    /**
     * Compute a set of artifact identifiers by adding the passed artifact id
     * string to this group id.
     *
     * @param artifactId An artifact id
     * @return An ArtifactIdentifiers
     */
    public ArtifactIdentifiers artifact(String artifactId)
    {
        return artifact(ArtifactId.of(artifactId));
    }

    /**
     * Compute a set of artifact identifiers by adding the passed artifact id to
     * this group id.
     *
     * @param artifactId An artifact id
     * @return An ArtifactIdentifiers
     */
    public ArtifactIdentifiers artifact(ArtifactId id)
    {
        return new ArtifactIdentifiers(this, id);
    }
}
