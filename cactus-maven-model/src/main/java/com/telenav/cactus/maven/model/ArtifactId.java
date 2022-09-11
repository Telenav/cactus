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
 *
 * @author Tim Boudreau
 */
public final class ArtifactId extends ResolvablePomElement<ArtifactId>
{
    ArtifactId(Node node)
    {
        super(node);
    }

    ArtifactId(String artifactId)
    {
        super(artifactId);
    }

    public static ArtifactId of(Node what)
    {
        return new ArtifactId(what);
    }

    public static ArtifactId of(String what)
    {
        return new ArtifactId(notNull("what", what));
    }

    @Override
    protected ArtifactId newInstance(String what)
    {
        return of(what);
    }

    public ArtifactIdentifiers inGroup(GroupId id)
    {
        return new ArtifactIdentifiers(id, this);
    }
}
