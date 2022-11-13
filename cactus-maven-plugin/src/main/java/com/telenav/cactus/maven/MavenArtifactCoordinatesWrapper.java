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
package com.telenav.cactus.maven;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.DiskResident;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.PomVersion;

import java.nio.file.Path;

import org.apache.maven.project.MavenProject;

/**
 * Wraps a MavenProject in implementations of MavenArtifactCoordinates and DiskResident so it can be consumed by
 * PublishChecker.
 *
 * @author Tim Boudreau
 */
public final class MavenArtifactCoordinatesWrapper implements MavenArtifactCoordinates,
        DiskResident
{
    // This class should not be public, but making it non-public breaks
    // reflection-based loading from the module path.
    private final MavenProject project;

    public MavenArtifactCoordinatesWrapper(MavenProject project)
    {
        this.project = project;
    }

    @Override
    public Path path()
    {
        return project.getFile().toPath();
    }

    public static MavenArtifactCoordinatesWrapper wrap(MavenProject prj)
    {
        return new MavenArtifactCoordinatesWrapper(prj);
    }

    public String toString()
    {
        return project.toString();
    }

    @Override
    public GroupId groupId()
    {
        return GroupId.of(project.getGroupId());
    }

    @Override
    public ArtifactId artifactId()
    {
        return ArtifactId.of(project.getArtifactId());
    }

    @Override
    public ThrowingOptional<String> resolvedVersion()
    {
        return ThrowingOptional.of(project.getVersion());
    }

    @Override
    public PomVersion version()
    {
        return PomVersion.of(project.getVersion());
    }
}
