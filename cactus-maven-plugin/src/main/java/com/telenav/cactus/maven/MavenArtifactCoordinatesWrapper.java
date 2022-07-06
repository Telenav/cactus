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
 * Wraps a MavenProject in implementations of MavenArtifactCoordinates and
 * DiskResident so it can be consumed by PublishChecker.
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