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
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.scope.Scope;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.git.GitCheckout.checkout;
import static com.telenav.cactus.scope.ProjectFamily.fromGroupId;
import static java.util.Collections.singleton;
import static org.apache.maven.plugins.annotations.LifecyclePhase.COMPILE;

/**
 * For debugging - prints all members of each family for each possible scope; if
 * run against an aggregator project, does so once for each child project that
 * is in a distinct family+repository combination. Pass
 * <code>-Ddetail=true</code> to print out projects within a given checkout as
 * well, for each family + scope + checkout.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "print-scopes", threadSafe = true)
@BaseMojoGoal("print-scopes")
public class PrintScopesMojo extends BaseMojo
{
    private static final SharedDataKey<ProjectTree> TREE_KEY = SharedDataKey.of(
            ProjectTree.class);

    private static final SharedDataKey<Set<FamilyAndScope>> SEEN_KEY = SharedDataKey
            .of(
                    Set.class);

    private static final class FamilyAndScope
    {
        private final Scope scope;

        private final ProjectFamily family;

        private final GitCheckout checkout;

        FamilyAndScope(Scope scope, ProjectFamily family,
                GitCheckout checkout)
        {
            this.scope = scope;
            this.family = family;
            this.checkout = checkout;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            final FamilyAndScope other = (FamilyAndScope) obj;
            if (this.scope != other.scope)
            {
                return false;
            }
            if (!Objects.equals(this.family, other.family))
            {
                return false;
            }
            return Objects.equals(this.checkout, other.checkout);
        }

        @Override
        public int hashCode()
        {
            int hash = 7;
            hash = 97 * hash + Objects.hashCode(this.scope);
            hash = 97 * hash + Objects.hashCode(this.family);
            hash = 97 * hash + Objects.hashCode(this.checkout);
            return hash;
        }
    }

    /**
     * If true, list each project underneath each checkout.
     */
    // -Ddetail is a maven convention for help and other things that just
    // print some info.  Please do not change back to cactus.detail.
    @Parameter(property = "detail", defaultValue = "false")
    private boolean printProjects;

    @Override
    public void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ProjectTree tree = tree(project);
        GitCheckout co = checkout(project.getBasedir()).get();
        ProjectFamily family = fromGroupId(project.getGroupId());
        for (Scope scope : Scope.values())
        {
            if (seen(family, scope, co))
            {
                continue;
            }
            emitMessage(
                    "\n----- scope '" + scope + "' for family '" + family + "' in "
                    + co.loggingName() + " -----");
            List<GitCheckout> matched = tree.matchCheckouts(scope, co, true,
                    singleton(family), project.getGroupId());
            for (GitCheckout gc : matched)
            {
                String nm = gc.name();
                if (nm.isEmpty())
                {
                    nm = "(root)";
                }
                emitMessage("  * " + nm);
                if (printProjects)
                {
                    tree.projectsWithin(gc).forEach(prj ->
                    {
                        if (fromGroupId(prj.groupId().text())
                                .equals(family))
                        {
                            emitMessage("    * " + prj.coordinates());
                        }
                    });
                }
            }
        }
    }

    private boolean seen(ProjectFamily family, Scope scope, GitCheckout co)
    {
        FamilyAndScope test = new FamilyAndScope(scope, family, co);
        Set<FamilyAndScope> set = sharedData().computeIfAbsent(SEEN_KEY,
                HashSet::new);
        return !set.add(test);
    }

    private ProjectTree tree(MavenProject prj)
    {
        // Cache this, as creating the tree is _very_ expensive.
        return sharedData().computeIfAbsent(TREE_KEY, () -> ProjectTree
                .from(prj)
                .get());
    }
}
