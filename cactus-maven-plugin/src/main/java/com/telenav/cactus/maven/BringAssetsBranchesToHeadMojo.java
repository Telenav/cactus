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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Change the branch of all non-maven git submodules.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "update-assets-checkouts", threadSafe = true)
public class BringAssetsBranchesToHeadMojo extends BaseMojo
{
    /**
     * The branch they should be on.
     */
    @Parameter(property = "telenav.assets-branch", defaultValue = "publish")
    private String assetsBranch;

    /**
     * Do a pull first?
     */
    @Parameter(property = "telenav.assets-pull", defaultValue = "false")
    private boolean pull;

    /**
     * Create a new commit in the submodule root that anchors the submodules on the head commit you have changed them
     * to.
     */
    @Parameter(property = "telenav.assets-commit", defaultValue = "true")
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
            tree.invalidateCache();
            if (!relativePaths.isEmpty() && commit && tree.root()
                    .hasUncommitedChanges())
            {
                System.out.println("ADD " + relativePaths.keySet());
                tree.root().add(relativePaths.keySet());
                tree.root().commit(
                        "Put assets projects on branch '" + assetsBranch + "'");
            }
        });
    }
}
