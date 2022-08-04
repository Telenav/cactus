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

/**
 * A thing that has a group id, an artifact id and a version.
 *
 * @author Tim Boudreau
 */
public interface MavenArtifactCoordinates extends MavenIdentified,
                                                  MavenVersioned
{

    default MavenArtifactCoordinates withVersion(String ver)
    {
        return new MavenArtifactCoordinates()
        {
            @Override
            public GroupId groupId()
            {
                return MavenArtifactCoordinates.this.groupId();
            }

            @Override
            public ArtifactId artifactId()
            {
                return MavenArtifactCoordinates.this.artifactId();
            }

            @Override
            public ThrowingOptional<String> resolvedVersion()
            {
                if (ver.contains("${"))
                {
                    return ThrowingOptional.empty();
                }
                return ThrowingOptional.of(ver);
            }

            @Override
            public PomVersion version()
            {
                return PomVersion.of(ver);
            }
        };
    }
}
