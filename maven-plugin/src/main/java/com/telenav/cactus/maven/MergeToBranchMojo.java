package com.telenav.cactus.maven;

import static com.telenav.cactus.maven.ForkBuildMojo.BRANCHED_REPOS_KEY;
import static com.telenav.cactus.maven.ForkBuildMojo.TARGET_BRANCH_KEY;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import static com.telenav.cactus.maven.ForkBuildMojo.TEMP_BRANCH_KEY;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * End-of-build correlate of ForkBuildMojo, which (since it only runs if the
 * build succeeds) merges the forked, merged branch back to the stable branch.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "merge", threadSafe = true)
public class MergeToBranchMojo extends ScopedCheckoutsMojo
{

    @Inject
    SharedData sharedData;

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout, ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        Optional<String> tempBranch = sharedData.remove(TEMP_BRANCH_KEY);
        Optional<String> targetBranch = sharedData.remove(TARGET_BRANCH_KEY);
        Optional<GitCheckout[]> ourCheckouts = sharedData.remove(BRANCHED_REPOS_KEY);
        System.out.println("\n\n\nDO THE THING\n\n\n\n");
        if (tempBranch.isPresent() && targetBranch.isPresent() && ourCheckouts.isPresent())
        {
            System.out.println("HAVE BRANCH " + tempBranch.get());
            System.out.println("HAVE TARGET " + targetBranch.get());
            System.out.println("HAVE REPOS " + Arrays.toString(ourCheckouts.get()));

            performMerge(tempBranch.get(), ourCheckouts.get(), targetBranch.get(), log.child("merge"));
        }
        System.out.println("\n\n\nDONE DID THE THING\n");
    }

    private void performMerge(String tempBranch, GitCheckout[] checkouts, String targetBranch, BuildLog log) throws MojoExecutionException
    {
        Set<GitCheckout> toMerge = new LinkedHashSet<>(Arrays.asList(checkouts));
        for (GitCheckout co : checkouts)
        {
            Optional<String> currBranch = co.branch();
            if (!currBranch.isPresent())
            {
                log.warn(co.name() + " should be on " + tempBranch + " but is not on a branch with head " + co.head());
                toMerge.remove(co);
//                fail(co.name() + " should be on " + tempBranch + " but is not on a branch with head " + co.head());
            } else
            {
                if (!tempBranch.equals(currBranch.get()))
                {
                    log.warn(co.name() + " should be on " + tempBranch + " but is not on a branch with head " + co.head());
//                    fail(co.name() + " should be on " + tempBranch + " but it is on the branch " + currBranch.get());
                    toMerge.remove(co);
                }
            }
            log.info("Check out target branch '" + targetBranch + "' in " + co.name());
            if (!isPretend())
            {
                co.switchToBranch(targetBranch);
            }
        }
        for (GitCheckout co : toMerge)
        {
            log.info("Merge " + tempBranch + " into " + targetBranch + " in " + co.name());
            if (!isPretend())
            {
                co.merge(tempBranch);
            }
        }
    }

}
