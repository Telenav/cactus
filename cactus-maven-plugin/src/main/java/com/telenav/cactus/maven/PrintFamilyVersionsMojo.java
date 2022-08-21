package com.telenav.cactus.maven;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.scope.ProjectFamily;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Prints the current "family-version" of scopes requested.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "print-versions", threadSafe = true)
@BaseMojoGoal("print-versions")
public final class PrintFamilyVersionsMojo extends ScopedCheckoutsMojo
{
    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        Map<ProjectFamily, PomVersion> versions = new TreeMap<>();

        families().forEach(family -> family.probableFamilyVersion(tree
                .allProjects()).ifPresent(ver -> versions.put(family, ver)));

        versions.forEach((family, currentVersion)
                -> emitMessage(family + " " + currentVersion)
        );
    }
}
