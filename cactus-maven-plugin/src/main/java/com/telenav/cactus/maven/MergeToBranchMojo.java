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

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.ForkBuildMojo.BRANCHED_REPOS_KEY;
import static com.telenav.cactus.maven.ForkBuildMojo.TARGET_BRANCH_KEY;
import static com.telenav.cactus.maven.ForkBuildMojo.TEMP_BRANCH_KEY;

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

    private void performMerge(String tempBranch, GitCheckout[] checkouts,
            String targetBranch, BuildLog log) throws MojoExecutionException
    {
        Set<GitCheckout> toMerge = new LinkedHashSet<>(Arrays.asList(checkouts));
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
