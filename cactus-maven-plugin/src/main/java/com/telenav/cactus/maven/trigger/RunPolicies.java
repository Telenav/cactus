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
package com.telenav.cactus.maven.trigger;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.FamilyAware;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.scope.ProjectFamily;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.BuildBase;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PLUGIN_NAME;

/**
 * Common implementations of RunPolicy.
 *
 * @author Tim Boudreau
 */
public enum RunPolicies implements RunPolicy
{
    INITIAL,
    /**
     * Run on first invocation.
     */
    FIRST,
    /**
     * Run against every project.
     */
    EVERY,
    /**
     * Run on every POM packaging project.
     */
    POM_PROJECT_ONLY,

    /**
     * Run only on projects that do NOT have packaging POM.
     */
    NON_POM_PROJECT_ONLY,

    /**
     * Run on project family root projects - a project family root is a
     * first-level git submodule containing a pom in its root.
     */
    FAMILY_ROOTS,

    /**
     * Run if this execution is the last execution this plugin will have in the
     * current maven session.
     */
    LAST_CONTAINING_GOAL,
    /**
     * Run when invoked against the last project (the one maven was initially
     * run against in the case of an aggregator project) - note that in the case
     * that the final aggregator project does not have the mojo using this
     * policy in its build/plugins section or in any active profile, then this
     * means the mojo using this policy will never be run at all.
     * <p>
     * Use this either when you are sure that is a non-problem (you only want it
     * to run at the tail end of builds of specific aggregator projects). The
     * logic is much simpler for this than LAST_CONTAINING_GOAL and it is likely
     * to make builds quicker, but if you want a <i>guarantee</i>
     * that your mojo runs, this is not the policy for you.
     */
    LAST,

    LAST_IN_ALL_PROJECTS,

    LAST_IN_SESSION_PROJECTS,;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    /**
     * Determine if the mojo should run.
     *
     * @param mojo The mojo
     * @param invokedOn The target project
     * @return True if it should run
     */
    @Override
    public boolean shouldRun(BaseMojo mojo, MavenProject invokedOn)
    {
        switch (this)
        {
            case INITIAL:
                return mojo.session().getAllProjects().indexOf(invokedOn) == 0;
            case FIRST:
                return mojo.isFirstRunInThisSession();
            case POM_PROJECT_ONLY:
                return "pom".equals(invokedOn.getPackaging());
            case NON_POM_PROJECT_ONLY:
                return !"pom".equals(invokedOn.getPackaging());
            case EVERY:
                return true;
            case FAMILY_ROOTS:
                return isFamilyRoot(mojo, invokedOn);
            case LAST_CONTAINING_GOAL:
                // Last project in session is faster and will be correct
                // on the last project in the session if it contains the goal
                return isLastProjectInSession(invokedOn, mojo.session())
                        || isLastProjectInSessionContaingThisMojosGoal(mojo,
                                invokedOn);
            case LAST:
                return isLastProjectInSession(invokedOn, mojo.session());
            case LAST_IN_ALL_PROJECTS:
                List<MavenProject> all = mojo.session().getAllProjects();
                return all.indexOf(invokedOn) == all.size() - 1;
            case LAST_IN_SESSION_PROJECTS:
                List<MavenProject> all2 = mojo.session().getProjects();
                return all2.indexOf(invokedOn) == all2.size() - 1;
            default:
                throw new AssertionError(this);
        }
    }

    private static boolean isLastProjectInSession(MavenProject invokedOn,
            MavenSession session)
    {
        boolean result = session.getExecutionRootDirectory().equalsIgnoreCase(
                invokedOn.getBasedir().toString());
        return result;
    }

    public static boolean isFamilyRoot(BaseMojo invokedBy,
            MavenProject invokedOn)
    {
        Map<Path, Boolean> cache = familyRootCache(invokedBy);
        try
        {
            if (!"pom".equals(invokedOn.getPackaging()) || invokedOn
                    .getModules().isEmpty())
            {
                return false;
            }
            return cache.computeIfAbsent(invokedOn.getBasedir().toPath(),
                    p -> computeIsFamilyRoot(invokedBy, p, invokedOn));
        }
        finally
        {
            if (RunPolicies.isLastProjectInSession(invokedOn, invokedBy
                    .session()))
            {
                cache.clear();
            }
        }
    }

    private static SharedDataKey<Map<Path, Boolean>> familyRootCacheKey(
            BaseMojo mojo)
    {
        return SharedDataKey.<Map<Path, Boolean>>of(mojo.getClass().getName(),
                Map.class);
    }

    private static Map<Path, Boolean> familyRootCache(BaseMojo mojo)
    {
        return mojo.sharedData().computeIfAbsent(familyRootCacheKey(mojo),
                ConcurrentHashMap::new);
    }

    private static boolean computeIsFamilyRoot(BaseMojo invokedBy, Path basedir,
            MavenProject prj)
    {
        if (invokedBy instanceof FamilyAware)
        {
            Set<ProjectFamily> families = ((FamilyAware) invokedBy).families();
            if (!families.isEmpty() && !families.contains(ProjectFamily
                    .fromGroupId(prj.getGroupId())))
            {
                return false;
            }
        }
        return GitCheckout.checkout(basedir)
                .map(co ->
                {
                    if (!basedir.equals(co.checkoutRoot()))
                    {
                        return false;
                    }
                    // In a standalone checkout of just, say, kivakit-stuff,
                    // we are a family root, but there ARE no submodules
                    if (co.isRoot())
                    {
                        boolean result = !co.isSubmodule();
                        return result;
                    }
                    boolean result = !co.isSubmoduleRoot();
                    if (result)
                    {
                        result = !co.name().contains("/");
                    }
                    return result;
                }).orElse(false);
    }

    private static boolean isLastProjectInSessionContaingThisMojosGoal(
            BaseMojo mojo,
            MavenProject invokedOn)
    {
        if (mojo.wasRunInThisSession())
        {
            return false;
        }
        String goalName = mojo.goal();
        if (goalName.isEmpty())
        {
            return false;
        }
        List<MavenProject> projects = mojo.session().getAllProjects();
        int max = projects.size() - 1;
        boolean found;
        for (int i = max; i >= 0; i--)
        {
            MavenProject prj = projects.get(i);
            found = usesGoal(prj, goalName);
            if (found && prj.getArtifactId().equals(invokedOn
                    .getArtifactId()) && prj.getGroupId().equals(invokedOn
                            .getGroupId()))
            {
                return true;
            }
            else
                if (found)
                {
                    break;
                }
        }
        return false;
    }

    private static boolean usesGoal(MavenProject p, String goal)
    {
        if (!containsGoal(p.getBuild(), goal))
        {
            for (Profile profile : p.getActiveProfiles())
            {
                if (containsGoal(profile.getBuild(), goal))
                {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private static boolean containsGoal(BuildBase bld, String goal)
    {
        if (bld == null)
        {
            return false;
        }
        for (Plugin pl : bld.getPlugins())
        {
            if (PLUGIN_NAME.equals(pl.getArtifactId()))
            {
                for (PluginExecution exe : pl.getExecutions())
                {
                    if (exe.getGoals().contains(goal))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
