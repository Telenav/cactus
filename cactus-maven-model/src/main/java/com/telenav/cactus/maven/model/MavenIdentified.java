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

import java.util.Objects;

/**
 * A thing which has a maven group id and artifact id.
 *
 * @author Tim Boudreau
 */
public interface MavenIdentified
{

    GroupId groupId();

    ArtifactId artifactId();
    
    default ArtifactIdentifiers toArtifactIdentifiers() {
        return new ArtifactIdentifiers(groupId(), artifactId());
    }

    default boolean is(MavenIdentified other)
    {
        return other == this || (Objects.equals(groupId(), other.groupId())
                && Objects.equals(artifactId(), other.artifactId()));
    }
}
