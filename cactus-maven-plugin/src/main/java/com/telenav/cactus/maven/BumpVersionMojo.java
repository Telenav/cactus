package com.telenav.cactus.maven;

import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.util.EnumMatcher;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Computes the new version of something updates files.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "bump-version", threadSafe = true)
public class BumpVersionMojo extends ReplaceMojo
{
    private static final EnumMatcher<VersionChangeMagnitude> MAGNITUDE_MATCHER = EnumMatcher
            .enumMatcher(VersionChangeMagnitude.class);

    private static final EnumMatcher<VersionFlavorChange> FLAVOR_MATCHER = EnumMatcher
            .enumMatcher(VersionFlavorChange.class);

    @Parameter(property = "cactus.version.change.magnitude",
            defaultValue = "minor")
    String versionChangeMagnitude = "minor";
    @Parameter(property = "cactus.version.flavor",
            defaultValue = "minor")
    String versionFlavor = "minor";

    boolean snapshot;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        super.onValidateParameters(log, project);
        VersionChangeMagnitude mag = magnitude();
        VersionFlavorChange flavor = flavor();
        if (mag.isNone() && flavor.isNone())
        {
            fail("Nothing to do for " + mag + " " + flavor);
        }
        PomVersion oldVersion = PomVersion.of(project.getVersion());
        PomVersion updatedVersion = oldVersion.updatedWith(mag, flavor)
                .orElseThrow(
                        () -> new MojoExecutionException("Applying " + mag + " "
                                + "+ " + flavor + " to version "
                                + oldVersion + " does not change anything"));

        log.info("Bump version of " + project.getGroupId() + ":" + project
                .getArtifactId() + " from "
                + oldVersion + " to " + updatedVersion);

        super.newVersion = updatedVersion.text();
        if (super.newBranchName == null && updatedVersion.flavor() == VersionFlavor.RELEASE)
        {
            super.newBranchName = "release/" + updatedVersion;
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        super.execute(log, project, myCheckout, tree, checkouts);
    }

    VersionChangeMagnitude magnitude()
    {
        return MAGNITUDE_MATCHER.match(versionChangeMagnitude,
                VersionChangeMagnitude.NONE);
    }

    VersionFlavorChange flavor()
    {
        return FLAVOR_MATCHER
                .match(versionFlavor, VersionFlavorChange.UNCHANGED);
    }

}
