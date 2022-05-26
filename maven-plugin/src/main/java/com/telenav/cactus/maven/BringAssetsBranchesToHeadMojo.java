package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "update-assets-checkouts", threadSafe = true)
public class BringAssetsBranchesToHeadMojo extends BaseMojo
{

    @Parameter(property = "telenav.assets.branch", defaultValue = "publish")
    private String assetsBranch;

    @Parameter(property = "telenav.assets.pull", defaultValue = "false")
    private boolean pull;

    @Parameter(property = "telenav.assets.commit", defaultValue = "true")
    private boolean commit;

    @Override
    protected boolean isOncePerSession()
    {
        return true;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ProjectTree.from(project).ifPresent(tree ->
        {
            Set<GitCheckout> nonMavenCheckouts = tree.nonMavenCheckouts();

            System.out.println("NON MAVEN CHECKOUTS: " + nonMavenCheckouts);
            Map<Path, String> relativePaths = new HashMap<>();
            for (GitCheckout checkout : nonMavenCheckouts)
            {
                checkout.submoduleRelativePath().ifPresent(path ->
                {
                    relativePaths.put(path, assetsBranch);
                    checkout.setSubmoduleBranch(path.toString(), assetsBranch);
                });
            }
            if (pull)
            {
                for (GitCheckout checkout : nonMavenCheckouts)
                {
                    checkout.pull();
                }
            }
            if (!relativePaths.isEmpty() && commit && tree.root().hasUncommitedChanges())
            {
                System.out.println("ADD " + relativePaths.keySet());
                tree.root().add(relativePaths.keySet());
                tree.root().commit("Put assets projects on branch '" + assetsBranch + "'");
            }
        });
    }
}
