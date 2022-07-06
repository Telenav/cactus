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

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.tree.ConsistencyChecker;
import com.telenav.cactus.maven.tree.ParentRelativePathChecker;
import com.telenav.cactus.maven.tree.Problem;
import java.util.LinkedHashSet;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.Set;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Check that the git repository tree is consistent and report details of any
 * inconsistencies. Inconsistencies are branch mismatches within a family, dirty
 * (locally modified) sources or mismatching version numbers within a group-id.
 * Used in preparation for a release to ensure all checkouts are in a known
 * state and there are no surprises.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "check-consistency", threadSafe = true)
@BaseMojoGoal("check-consistency")
public class CheckConsistencyMojo extends SharedProjectTreeMojo
{

    /**
     * Comma-delimited suffix list for checkout folder names that should be
     * ignored when checking branch consistency, such as assets checkouts.
     */
    @Parameter(property = "cactus.ignore-in-branch-consistency-check",
            defaultValue = "-assets")
    private String ignoreInBranchConsistencyCheck = "";

    /**
     * Comma-delimited list of artifact ids which should be ignored when
     * checking version consistency.
     */
    @Parameter(property = "cactus.ignore-in-version-consistency-check"
    )
    private String ignoreInVersionConsistencyCheck = "";

    /**
     * If true, check all group ids, not just checkouts containing a project
     * with the same group id as the one owning the project this mojo is being
     * run against.
     */
    @Parameter(property = "cactus.all-group-ids", defaultValue = "false")
    private boolean allGroupIds = false;

    public CheckConsistencyMojo()
    {
        super(true);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {

        ConsistencyChecker checker = new ConsistencyChecker(
                ignoreInBranchConsistencyCheck,
                ignoreInVersionConsistencyCheck, allGroupIds
                                                 ? null
                                                 : project.getGroupId(), true);

        Set<Problem> inconsistencies = new LinkedHashSet<>(checker
                .checkConsistency(project, log, projectTree()));
        log.info("Check parent relative paths");
        projectTree().ifPresent(tree ->
        {
            inconsistencies.addAll(new ParentRelativePathChecker().checkTree(
                    tree));
        });
        if (!inconsistencies.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (Problem issue : inconsistencies)
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(issue);
            }
            log.error(sb.toString());
            fail(sb.toString());
        }
    }
}
