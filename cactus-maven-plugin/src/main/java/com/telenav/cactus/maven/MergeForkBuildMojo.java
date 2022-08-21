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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.ForkBuildMojo.BRANCHED_REPOS_KEY;
import static com.telenav.cactus.maven.ForkBuildMojo.TARGET_BRANCH_KEY;
import static com.telenav.cactus.maven.ForkBuildMojo.TEMP_BRANCH_KEY;
import static java.util.Arrays.asList;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PREPARE_PACKAGE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * End-of-build correlate of ForkBuildMojo, which (since it only runs if the build succeeds) merges the forked, merged
 * branch back to the stable branch.
 * <p>
 * Note that this mojo <b>requires</b> that {@link ForkBuildMojo} be run at an earlier phase of the build, and will fail
 * if it has not.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = PREPARE_PACKAGE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "finish-attempt-merge", threadSafe = true)
@BaseMojoGoal("finish-attempt-merge")
public class MergeForkBuildMojo extends ScopedCheckoutsMojo
{
    private final String DID_NOT_RUN_MESSAGE
            = MergeForkBuildMojo.class.getSimpleName()
            + " requires that "
            + ForkBuildMojo.class.getSimpleName() + " was run at an earlier "
            + "phase of the build, but no data provided by it was found to "
            + "determine what needs to be merged.";

    @Inject
    SharedData sharedData;

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout, ProjectTree tree,
                           List<GitCheckout> checkouts) throws Exception
    {
        Optional<String> tempBranch = sharedData.remove(TEMP_BRANCH_KEY);
        Optional<String> targetBranch = sharedData.remove(TARGET_BRANCH_KEY);
        Optional<GitCheckout[]> ourCheckouts = sharedData.remove(
                BRANCHED_REPOS_KEY);
        if (tempBranch.isPresent() && targetBranch.isPresent() && ourCheckouts
                .isPresent())
        {
            performMerge(tempBranch.get(), ourCheckouts.get(), targetBranch
                    .get(), log.child("merge"));
        }
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        if (sharedData == null)
        {
            fail("Shared data not injected");
        }
        String baseMessage = MergeForkBuildMojo.class.getSimpleName() + " requires that "
                + ForkBuildMojo.class.getSimpleName() + " was run at an earlier "
                + "phase of the build, but no data provided by it was found to "
                + "determine what needs to be merged.";
        sharedData.ensureHas(TEMP_BRANCH_KEY, baseMessage);
        sharedData.ensureHas(TARGET_BRANCH_KEY, baseMessage);
        sharedData.ensureHas(BRANCHED_REPOS_KEY, baseMessage);
    }

    private void performMerge(String tempBranch, GitCheckout[] checkouts,
                              String targetBranch, BuildLog log) throws MojoExecutionException
    {
        Set<GitCheckout> toMerge = new LinkedHashSet<>(asList(checkouts));
        for (GitCheckout co : checkouts)
        {
            Optional<String> currBranch = co.branch();
            if (!currBranch.isPresent())
            {
                log.warn(
                        co.name() + " should be on " + tempBranch
                                + " but is not on a branch with head "
                                + co.head());
                toMerge.remove(co);
                //                fail(co.name() + " should be on " + tempBranch + " but is not on a branch with head " + co.head());
            }
            else
            {
                if (!tempBranch.equals(currBranch.get()))
                {
                    log.warn(
                            co.name() + " should be on " + tempBranch
                                    + " but is not on a branch with head "
                                    + co.head());
                    //                    fail(co.name() + " should be on " + tempBranch + " but it is on the branch " + currBranch.get());
                    toMerge.remove(co);
                }
            }
            log.info("Check out target branch '" + targetBranch + "' in "
                    + co.name());
            if (!isPretend())
            {
                co.switchToBranch(targetBranch);
            }
        }
        for (GitCheckout co : toMerge)
        {
            log.info(
                    "Merge " + tempBranch + " into " + targetBranch + " in " + co
                            .name());
            if (!isPretend())
            {
                co.merge(tempBranch);
            }
        }
    }
}
