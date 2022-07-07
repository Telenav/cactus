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
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Change the branch of all non-maven git submodules.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "update-assets-checkouts", threadSafe = true)
@BaseMojoGoal("update-assets-checkouts")
public class BringAssetsBranchesToHeadMojo extends BaseMojo
{
    /**
     * The branch they should be on.
     */
    @Parameter(property = "cactus.assets-branch", defaultValue = "publish")
    private String assetsBranch;

    /**
     * Do a pull first?
     */
    @Parameter(property = "cactus.assets-pull", defaultValue = "false")
    private boolean pull;

    /**
     * Create a new commit in the submodule root that anchors the submodules on
     * the head commit you have changed them to.
     */
    @Parameter(property = "cactus.assets-commit", defaultValue = "true")
    private boolean commit;

    public BringAssetsBranchesToHeadMojo()
    {
        super(true);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        withProjectTree(tree ->
        {
            Set<GitCheckout> nonMavenCheckouts = tree.nonMavenCheckouts();

            Map<Path, String> relativePaths = new HashMap<>();
            Set<GitCheckout> toUse = new LinkedHashSet<>();
            for (GitCheckout checkout : nonMavenCheckouts)
            {
                if (checkout.hasPomInRoot())
                {
                    continue;
                }
                checkout.submoduleRelativePath().ifPresent(path ->
                {
                    relativePaths.put(path, assetsBranch);
                    checkout.setSubmoduleBranch(path.toString(), assetsBranch);
                    toUse.add(checkout);
                });
            }
            if (toUse.isEmpty())
            {
                log.warn("Nothing to update");
                return;
            }
            if (pull)
            {
                for (GitCheckout checkout : toUse)
                {
                    checkout.pull();
                }
            }
            tree.invalidateCache();
            if (!relativePaths.isEmpty() && commit && tree.root()
                    .hasUncommitedChanges())
            {
                tree.root().add(relativePaths.keySet());
                tree.root().commit(
                        "Put assets projects on branch '" + assetsBranch + "'");
            }
        });
    }
}
