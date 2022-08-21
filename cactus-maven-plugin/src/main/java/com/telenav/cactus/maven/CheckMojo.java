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

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.FamilyAwareMojo;
import com.telenav.cactus.maven.tree.ConsistencyChecker2;
import com.telenav.cactus.maven.tree.Problems;
import com.telenav.cactus.util.EnumMatcher;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.function.optional.ThrowingOptional.empty;
import static com.mastfrog.function.optional.ThrowingOptional.of;
import static com.telenav.cactus.maven.trigger.RunPolicies.FIRST;
import static com.telenav.cactus.scope.ProjectFamily.fromCommaDelimited;
import static com.telenav.cactus.util.EnumMatcher.enumMatcher;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Performs a set of sanity checks of project tree state useful before merging
 * or releasing. Specifically:
 * <ul>
 * <li>Ensure there are no remote modifications that need to be pulled</li>
 * <li>Ensure that all checkouts not containing superpoms belong to the same
 * family</li>
 * <li>Ensure that all checkouts within each family targeted are on the same
 * branch</li>
 * <li>Ensure that no intermediate pom-packaging poms, which should be simple
 * bills of materials, are used as a parent</li>
 * <li>Ensure that no checkout is in detached-head state</li>
 * <li>Ensure that no checkout has local changes</li>
 * </ul>
 * <p>
 * Each of these checks can be turned off with the appropriate boolean
 * parameter; all are on by default.
 * </p><p>
 * Caveats: Poms in a targeted family in a checkout containing superpoms are
 * permitted to deviate from the version and branch schemas, as it is normal
 * that these are shared between multiple families.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "check", threadSafe = true)
@BaseMojoGoal("check")
public class CheckMojo extends FamilyAwareMojo
{
    private static final EnumMatcher<VersionFlavor> FLAVOR_MATCHER
            = enumMatcher(VersionFlavor.class);
    /**
     * Fail if there are remote changes that have not been pulled.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.remote", defaultValue = "true")
    private boolean checkRemoteModifications = true;

    /**
     * Fail if there are poms in more than one family in a checkout that does
     * not contain superpoms.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.families", defaultValue = "true")
    private boolean checkFamilies = true;

    /**
     * Check that intermediate pom-packaging poms are not used as parents by
     * anything where they are not the root superpom but do have a non-empty
     * modules section.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.roles", defaultValue = "true")
    private boolean checkRoles = true;

    /**
     * Fail if there are non-superpom poms in a family that do not share the
     * same version as the rest.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.versions", defaultValue = "true")
    private boolean checkVersions = true;

    /**
     * Fail if any checkout of any matched pom is in detached-head state.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.detached", defaultValue = "true")
    private boolean checkDetached = true;

    /**
     * Fail if any non-superpom-containing checkout in the same family is on a
     * different branch than the rest.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.branches", defaultValue = "true")
    private boolean checkBranches = true;

    /**
     * Fail if the parent relativePath of any superpom either points to a
     * non-existing file, or to an existing file that is not part of the same
     * git checkout.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.paths", defaultValue = "true")
    private boolean checkRelativePaths = true;

    /**
     * Fail if the checkout that owns any matched pom has local modifications.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Parameter(property = "cactus.check.dirty", defaultValue = "true")
    private boolean checkDirty = true;

    /**
     * If present, check will ensure the given scope is on that branch.
     */
    @Parameter(property = "cactus.expected.branch")
    private String expectedBranch;

    /**
     * A family with 1-2 members may contain divergent versions as a matter of
     * course. Flag these in a comma delimited list here to make such issues a
     * note rather than a fatal problem.
     */
    @Parameter(property = "cactus.tolerate.version.inconsistencies.families"
    )
    private String tolerateVersionInconsistenciesIn;

    /**
     * For testing other mojos, it may be useful to skip the sanity check.
     */
    @Parameter(property = "cactus.check.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Enforce that all versions in the matched poms are of this version
     * <i>flavor</i>, particularly <code>snapshot</code> or
     * <code>release</code>.
     */
    @Parameter(property = "cactus.version.flavor")
    private String versionFlavor;

    public CheckMojo()
    {
        super(FIRST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (skip)
        {
            return;
        }
        ConsistencyChecker2 c = new ConsistencyChecker2()
                .activityLogger(log::info)
                .withTargetBranch(expectedBranch);
        if (checkRelativePaths)
        {
            c.checkRelativePaths();
        }
        if (checkFamilies)
        {
            c.checkFamilies();
        }
        if (checkRemoteModifications)
        {
            c.checkRemoteModifications();
        }
        if (checkRoles)
        {
            c.checkRoles();
        }
        if (checkVersions)
        {
            c.checkVersions();
        }
        if (checkDetached)
        {
            c.checkDetached();
        }
        if (checkBranches)
        {
            c.checkBranches();
        }
        if (checkDirty)
        {
            c.checkLocalModifications();
        }
        flavor().ifPresent(c::enforceVersionFlavor);
        if (tolerateVersionInconsistenciesIn != null)
        {
            c.tolerateVersionInconsistenciesIn(fromCommaDelimited(
                            tolerateVersionInconsistenciesIn, () -> null));
        }
        if (hasExplicitFamilies())
        {
            c.checkFamilies(families());
        }

        withProjectTree(tree ->
        {
            Problems probs = c.check(tree);
            if (probs.hasFatal())
            {
                String s = probs.toString();
                log.error(s);
                fail(s);
            }
        });
    }

    ThrowingOptional<VersionFlavor> flavor()
    {
        if (versionFlavor != null && !versionFlavor.isBlank())
        {
            return of(FLAVOR_MATCHER
                    .matchOrThrow(versionFlavor));
        }
        return empty();
    }
}
