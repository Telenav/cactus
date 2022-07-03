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
 *
 * @author timb
 */
final class MavenArtifactCoordinatesWrapper implements MavenArtifactCoordinates,
                                                       DiskResident
{
    private final MavenProject project;

    MavenArtifactCoordinatesWrapper(MavenProject project)
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
