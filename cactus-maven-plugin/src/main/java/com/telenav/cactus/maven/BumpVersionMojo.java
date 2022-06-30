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

import com.mastfrog.function.state.Obj;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.commit.CommitMessage.Section;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.refactoring.SuperpomBumpPolicy;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.util.EnumMatcher;
import com.telenav.cactus.maven.refactoring.VersionMismatchPolicy;
import com.telenav.cactus.maven.refactoring.VersionMismatchPolicyOutcome;
import com.telenav.cactus.maven.refactoring.VersionReplacementFinder;
import com.telenav.cactus.maven.refactoring.VersionUpdateFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.COMMIT_CHANGES;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static com.telenav.cactus.scope.Scope.FAMILY;
import static com.telenav.cactus.scope.Scope.FAMILY_OR_CHILD_FAMILY;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Computes the new version of projects in the requested scope and updates pom
 * files and documentation files.
 * <p>
 * If the new version is a release version, the release branch name is
 * automatically computed unless passed explicitly.
 * </p><p>
 * Assumes three-decimal semantic versioning versions, e.g. 1.5.3 corresponding
 * to major-version, minor-version, dot-version.
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "bump-version", threadSafe = true)
public class BumpVersionMojo extends ReplaceMojo
{
    // Some matchers to allow friendly string forms of enum constants
    private static final EnumMatcher<VersionChangeMagnitude> MAGNITUDE_MATCHER
            = EnumMatcher.enumMatcher(VersionChangeMagnitude.class);

    private static final EnumMatcher<VersionFlavorChange> FLAVOR_MATCHER
            = EnumMatcher.enumMatcher(VersionFlavorChange.class);

    private static final EnumMatcher<VersionMismatchPolicyOutcome> MISMATCH_MATCHER
            = EnumMatcher.enumMatcher(VersionMismatchPolicyOutcome.class);

    private static final EnumMatcher<SuperpomBumpPolicy> SUPERPOM_POLICY_MATCHER
            = EnumMatcher.enumMatcher(SuperpomBumpPolicy.class);

    /**
     * The magnitude of the decimal to change (subsequent ones will be zeroed).
     * Possible values:
     * <ul>
     * <li>major</li>
     * <li>minor</li>
     * <li>dot</li>
     * <li>none - in which case the flavor change must be set to something that
     * will result in a new version or the mojo will fail the build</li>
     * </li>
     * The default is "dot".
     */
    @Parameter(property = "cactus.version.change.magnitude",
            defaultValue = "dot")
    String versionChangeMagnitude;

    /**
     * The kind of change to make to the <i>flavor</i> (represented by a suffix
     * such as "snapshot"). Possible values:
     * <ul>
     * <li>to-opposite - if on a snapshot, switch to a release version (no
     * suffix). Caveat: If the suffix is something other than -SNAPSHOT this
     * results in no change</li>
     * <li>to-release - remove any snapshot</li>
     * <li>to-snapshot - add the -SNAPSHOT suffix (see note about the decimal
     * portion below)</li>
     * <li>unchanged - make no change to the suffix</li>
     * </ul>
     * Going from release to snapshot _always_ results in a minor version bump,
     * even if you specified <code>none</code> for the decimal version change -
     * you can go from, say, 1.2.3-SNAPSHOT to a release version 1.2.3, but the
     * plugin will not go backwards to a snapshot version, instead changing it
     * to 1.2.4-SNAPSHOT (if you specified major or minor instead, you will get
     * the result of that transformation - this is only in the case that no
     * decimal change was requested at all).
     */
    @Parameter(property = "cactus.version.flavor.change",
            defaultValue = "unchanged")
    String versionFlavor;

    /**
     * Defines what to do in the case of a version mismatch, which occurs when
     * the version in a pom the mojo expects to be updating from X to Y is not
     * X. The possible values are:
     * <ul>
     * <li>skip - make no changes to the version of the file</li>
     * <li>bring-to-target-version - ignore the version in the file and set it
     * to whatever is being used for other poms in that family</li>
     * <li>bump - increment version by whatever criteria were used for
     * everything else</li>
     * <li>abort - fail the build, making no changes to any pom files</li>
     * </ul>
     * There are two kinds version mismatches which are special cases:
     * <ol>
     * <li>The version is already at the requested version</li>
     * <li>The version is different, but in a pom file which is pure
     * configuration - it is used directly or indirectly as a parent pom, has
     * the packaging type <code>pom</code> but does not declare any modules - it
     * is a superpom or the ancestor superpom of something in a family being
     * changed. In that case, the version is simply bumped using the same flavor
     * and magnitude change requested for everything else - it is expected that
     * superpoms may be versioned independently of projects relying upon
     * them.</li>
     * </ol>
     */
    @Parameter(property = "cactus.version.mismatch.policy",
            defaultValue = "bump")
    String versionMismatchPolicy;

    /**
     * By default, this mojo walks the closure of all poms that are affected by
     * the requested change, and bumps versions of any superpoms with updated
     * properties, and then walks thorugh all their children updating their
     * parent versions, so that absolutely everything in the tree expects a
     * consistent set of versions of things.
     * <p>
     * If you want to update one project family _without_ altering any of the
     * others, set this to true and updates to any poms outside the family will
     * be skipped, regardless of the consequences for the buildability of the
     * result.
     * </p>
     */
    @Parameter(property = "cactus.version.single.family")
    boolean singleFamily;

    public BumpVersionMojo()
    {
        super(true);
    }

    /**
     * If a superpom is updated, it should get its version bumped (along with
     * all projects that use that superpom as a parent) so all of its children -
     * particularly on other machines not where this code is running, which may
     * have stale local versions of that superpom or its children that would not
     * contain the updated property.
     * <p>
     * This property describes what to do to the version of a superpom in or out
     * of a family being updated, which needs its version bumped because of a
     * property change.
     * </p>
     * <p>
     * Possible values:
     * </p>
     * <ul>
     * <li><code>bump-acquiring-new-family-flavor</code> - the bumped superpom
     * version should acquire the flavor/suffix the family is acquiring - i.e.
     * if we are changing the version of all projects in the family from 2.0.0
     * to 2.0.1-SNAPSHOT and we have a superpom with version 1.6.3 in the same
     * family (usually this means same groupId), then change the superpom's
     * version to 1.6.4-SNAPSHOT. If the superpom being changed is not part of
     * the family, then just bump its version by one dot revision - i.e. if
     * mesakit-superpom-1.5.1 contains a property <code>kivakit.version</code>,
     * it becomes mesakit-superpom-1.5.2 because it is not part of the family
     * <code>kivakit</code> - the version is bumped but the suffix is left
     * alone.  <i>If in the same family</i> magnitude of the superpom version
     * change will be the same as that of the family - so if we are going from
     * family 2.3.1 to 2.4.0, we will go from superpom 1.6.3 to superpom 1.7.0
     * (the second digit is what's changing in both).</li>
     * <li><code>bump-without-changing-flavor</code> - for superpoms that don't
     * follow the versioning scheme of the family they are superpom for, simply
     * increment the verson by one dot-revision. <i>If in the same family</i>
     * magnitude of the superpom version change will be the same as that of the
     * family - so if we are going from family 2.3.1 to 2.4.0, we will go from
     * superpom 1.6.3 to superpom 1.7.0 (the second digit is what's changing in
     * both).</li>
     * <li><code>ignore</code> - this is DANGEROUS and will probably result in a
     * source tree that does not build without manually fixing things, or at
     * best, will result in commiting changes that may result in things building
     * for you but not for other people until they clean and rebuild everything
     * or download new snapshots. With this option, simply does not make any
     * changes to superpoms unless they are part of the family being updated.
     * </li>
     * </ul>
     *
     */
    @Parameter(property = "cactus.superpom.bump.policy",
            defaultValue = "bump-without-changing-flavor")
    String bumpPolicy;

    /**
     * Explicitly set the version you want to change the family to, overriding
     * the version change magnitude and similar. In general, this is not needed,
     * as the plugin will always compute a correct new version based on what you
     * tell it to do. This property can only be used when updating only a single
     * project or project family - attempting to apply the same version to a
     * bunch of heterogenous projects is assumed to be operator error and will
     * fail the build (you can always do it one by one if that's really what you
     * want).
     */
    @Parameter(property = "cactus.explicit.version", required = false)
    String explicitVersion;

    /**
     * Perform substitutions in documentation files.
     */
    @Parameter(property = "cactus.update.docs", defaultValue = "true")
    boolean updateDocs;

    /**
     * Generate commits with the new version changes for each repository
     * modified.
     */
    @Parameter(property = COMMIT_CHANGES, defaultValue = "false")
    boolean commit;

    /**
     * Create a new release branch, if the version is a release and we are in
     * single-family mode.
     */
    @Parameter(property = "cactus.create.release.branch", defaultValue = "false")
    boolean createReleaseBranch;

    /**
     * Only used when creating a release branch.
     */
    @Parameter(property = "cactus.development.branch", defaultValue = "develop")
    String developmentBranch;

    /**
     * Allows to bump the version of multiple families in one pass - if you have
     * properties for the versions of things from multiple families in your
     * superpoms, this allows a single update to those superpoms to take care of
     * updates to more than one family without bumping their version once to set
     * up one set of properties, and again to set up another.
     */
    @Parameter(property = "cactus.families", required = false)
    String families;

    private VersionUpdateFilter filter()
    {
        if (singleFamily)
        {
            return VersionUpdateFilter.withinFamilyOrParentFamily(
                    projectFamily());
        }
        else
        {
            return VersionUpdateFilter.DEFAULT;
        }
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        super.onValidateParameters(log, project);

        if (family != null && families != null)
        {
            fail("Can use one of family or families, not both");
        }

        switch (scope())
        {
            case ALL:
            case ALL_PROJECT_FAMILIES:
            case JUST_THIS:
            case SAME_GROUP_ID:
            case FAMILY_OR_CHILD_FAMILY:
                if (families != null && !families.trim().isEmpty())
                {
                    fail("cactus.families can only be used with cactus.scope=family");
                }
                break;
            default:
                if (createReleaseBranch && families().size() > 1)
                {
                    fail("cactus.families cannot be used together with createReleaseBranch");
                }
        }

        VersionChangeMagnitude mag = magnitude();
        VersionFlavorChange flavor = flavor();

        if (mag.isNone() && flavor.isNone())
        {
            fail("Nothing to do for " + mag + " " + flavor);
        }
        // Pending - this should probably be done later, and use the set of
        // versions for the project tree - will work fine if only bumping
        // a single family, though, which is the common case.
        PomVersion oldVersion = PomVersion.of(project.getVersion());
        PomVersion updatedVersion;
        if (explicitVersion != null)
        {
            updatedVersion = PomVersion.of(explicitVersion);
            if (!updatedVersion.isValidVersion())
            {
                fail("'" + explicitVersion + "' is not a valid maven version");
            }
        }
        else
        {
            updatedVersion = oldVersion.updatedWith(mag, flavor)
                    .orElseThrow(
                            () -> new MojoExecutionException(
                                    "Applying " + mag + " "
                                    + "+ " + flavor + " to version "
                                    + oldVersion + " does not change anything"));
        }

        VersionChange vc = new VersionChange(oldVersion, updatedVersion);
        // Allow version changes to be logged by things that use them
        session().getAllProjects().forEach(prj ->
        {
            project.getProperties().put("cactus.version.change.description",
                    projectFamily() + " " + vc);
        });

        log.info("Bump version of " + project.getGroupId() + ":" + project
                .getArtifactId() + " from "
                + oldVersion + " to " + updatedVersion);

        super.newVersion = updatedVersion.text();
        if (super.newBranchName == null && updatedVersion.flavor() == VersionFlavor.RELEASE)
        {
            super.newBranchName = "release/" + updatedVersion;
        }
    }

    private PomVersion newVersion(Pom pom)
    {
        if (explicitVersion != null && projectFamily().equals(familyOf(pom)))
        {
            return PomVersion.of(explicitVersion);
        }
        else
            if (explicitVersion != null)
            {
                throw new IllegalStateException(
                        "Explicit version can only be used when altering a "
                        + " SINGLE project family, but have family " + projectFamily() + " plus "
                        + familyOf(pom) + " from " + pom);
            }

        VersionChangeMagnitude mag = magnitude();
        VersionFlavorChange flavor = flavor();
        PomVersion oldVersion = pom.version();
        PomVersion updatedVersion = oldVersion.updatedWith(mag, flavor)
                .orElseThrow(
                        () -> new IllegalStateException("Applying " + mag + " "
                                + "+ " + flavor + " to version "
                                + oldVersion + " does not change anything"));
        return updatedVersion;
    }

    private Optional<PomVersion> findVersionOfFamily(ProjectTree tree)
    {
        return findVersionOfFamily(tree, projectFamily());
    }

    private Optional<PomVersion> findVersionOfFamily(ProjectTree tree,
            ProjectFamily family)
    {
        return family.probableFamilyVersion(tree.allProjects());
    }

    private Set<ProjectFamily> allFamilies(ProjectTree tree)
    {
        Set<ProjectFamily> families = new HashSet<>();
        tree.allProjects().forEach(pom -> families.add(ProjectFamily
                .familyOf(pom.groupId())));
        return families;
    }

    private Set<ProjectFamily> familyWithChildFamilies(ProjectTree tree)
    {
        ProjectFamily fam = projectFamily();
        Set<ProjectFamily> relatives = new HashSet<>();
        tree.allProjects().forEach(pom
                -> fam.ifParentFamilyOf(pom.groupId(),
                        () -> relatives.add(ProjectFamily
                                .familyOf(pom.groupId())))
        );
        relatives.add(fam);
        return relatives;
    }

    private void checkCheckoutStates(String messageHead,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        StringBuilder failMessage = new StringBuilder();
        for (GitCheckout co : checkouts)
        {
            if (co.isSubmoduleRoot())
            {
                continue;
            }
            String nm = co.name();
            if (tree.isDetachedHead(co))
            {
                failMessage.append('\n');
                failMessage.append(nm).append(" is in detached head state");
            }
            else
                if (tree.isDirty(co))
                {
                    if (commit)
                    {
                        failMessage.append('\n');
                        failMessage.append(nm).append(
                                " has local modifications, and a commit has been requested");
                    }
                }
        }
        if (failMessage.length() > 0)
        {
            failMessage.insert(0, messageHead);
            fail(failMessage.toString());
        }
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        log.info("Checking repositories' state");
        checkCheckoutStates("Some git checkouts are not in a usable "
                + "state for generating version changes", tree, checkouts);
        // PENDING:  Should fail on local modifications
        log.info("Building index of all pom.xmls");
        // The thing that will rewrite the pom files
        VersionReplacementFinder replacer
                = new VersionReplacementFinder(new Poms(tree.allProjects()))
                        .withVersionMismatchPolicy(mismatchPolicy())
                        .withSuperpomBumpPolicy(superpomBumpPolicy())
                        .withFilter(filter());
        log.info(
                "Computing changes for " + magnitude() + " " + flavor() + " " + mismatchPolicy());
        if (explicitVersion != null && scope().canBeMultiFamily())
        {
            fail("Cannot use an explicit version with a scope that "
                    + "can match more than one family, such as '" + scope() + "'."
                    + " Only '" + FAMILY + "' or '" + FAMILY_OR_CHILD_FAMILY + "' are "
                    + "legal if you pass a specific version to change to.");
        }
        Obj<PomVersion> singleVersion = Obj.create();
        // Set up version changes for the right things based on the scope:
        switch (scope())
        {
            case JUST_THIS:
                Pom myPom = Pom.from(project.getFile().toPath()).get();

                PomVersion myNewVersion = newVersion(myPom);

                replacer.withSinglePomChange(myPom, myNewVersion);
                singleVersion.accept(myNewVersion);
                break;
            case SAME_GROUP_ID:
                tree.projectsForGroupId(project.getGroupId()).forEach(pom ->
                {
                    PomVersion nv = newVersion(pom);
                    singleVersion.set(nv);
                    replacer.withSinglePomChange(pom, nv);
                });
                break;
            case FAMILY:
                for (ProjectFamily fam : families())
                {
                    System.out.println("\nBUMP FAMILY " + fam + "\n");
                    findVersionOfFamily(tree, fam).ifPresent(v ->
                    {
                        PomVersion nue;
                        if (explicitVersion != null)
                        {
                            nue = PomVersion.of(explicitVersion);
                        }
                        else
                        {
                            nue = v.updatedWith(
                                    magnitude(),
                                    flavor())
                                    .get();
                        }
                        singleVersion.set(v);
                        replacer.withFamilyVersionChange(fam,
                                v,
                                nue);
                    });
                }
                break;
            case FAMILY_OR_CHILD_FAMILY:
                familyWithChildFamilies(tree).forEach(family ->
                {
                    findVersionOfFamily(tree, family).ifPresent(familyVersion ->
                    {
                        PomVersion newFamilyVersion = familyVersion.updatedWith(
                                magnitude(),
                                flavor()).get();
                        singleVersion.accept(newFamilyVersion);
                        replacer.withFamilyVersionChange(family,
                                familyVersion,
                                newFamilyVersion);
                    });
                });
                break;
            case ALL:
            case ALL_PROJECT_FAMILIES:
                allFamilies(tree).forEach(family ->
                {
                    findVersionOfFamily(tree, family).ifPresent(familyVersion ->
                    {
                        PomVersion newFamilyVersion = familyVersion.updatedWith(
                                magnitude(),
                                flavor()).get();

                        singleVersion.accept(newFamilyVersion);
                        replacer.withFamilyVersionChange(family,
                                familyVersion,
                                newFamilyVersion);
                    });
                });
                break;
            default:
                throw new AssertionError(scope());
        }
        if (isIncludeRoot())
        {
            if (tree.root().isSubmoduleRoot() && tree.root().hasPomInRoot())
            {
                tree.projectOf(tree.root().checkoutRoot().resolve("pom.xml"))
                        .ifPresent(rootPom ->
                        {
                            PomVersion newRootVersion = rootPom.version()
                                    .updatedWith(magnitude(),
                                            flavor()).get();
                            replacer
                                    .withSinglePomChange(rootPom, newRootVersion);
                        });
            }
        }
        replacer.pretend(isPretend());
        log.info("Applying changes");
        log.info(replacer.toString());
        Set<Path> rewritten = replacer.go(log::info);
        // Pending:  Construct new MavenProject instances and call 
        // session().setAllProjects() with them, so the build can proceed if
        // possible?

        // Use the set of checkouts that contain files that were actually modified,
        // so we don't generate substitution changes in checkouts we did not
        // make changes in
        Set<GitCheckout> owners = GitCheckout.ownersOf(rewritten);
        if (updateDocs)
        {
            runSubstitutions(log, project, myCheckout, tree, new ArrayList<>(
                    owners));
        }
        if (commit)
        {
            generateCommit(owners, replacer, singleVersion.toOptional());
        }
        tree.invalidateCache();
    }

    private void runSubstitutions(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        super.execute(log, project, myCheckout, tree, checkouts);
    }

    VersionChangeMagnitude magnitude()
    {
        Optional<VersionChangeMagnitude> mag = MAGNITUDE_MATCHER.match(
                versionChangeMagnitude);
        if (!mag.isPresent())
        {
            throw new IllegalArgumentException(
                    "Unrecognized magnitude change flavoer '" + versionChangeMagnitude + "'");
        }
        return mag.get();
    }

    Set<ProjectFamily> families()
    {
        Set<ProjectFamily> result = new HashSet<>();

        if (families != null)
        {
            for (String s : families.split(","))
            {
                s = s.trim();
                if (!s.isEmpty())
                {
                    result.add(ProjectFamily.named(s));
                }
            }
        }
        else
        {
            result.add(projectFamily());
        }
        return result;
    }

    VersionFlavorChange flavor()
    {
        return FLAVOR_MATCHER.matchOrThrow(versionFlavor);
    }

    VersionMismatchPolicy mismatchPolicy()
    {
        return MISMATCH_MATCHER.matchOrThrow(versionMismatchPolicy);
    }

    SuperpomBumpPolicy superpomBumpPolicy()
    {
        return SUPERPOM_POLICY_MATCHER.matchOrThrow(this.bumpPolicy);
    }

    private void generateCommit(Set<GitCheckout> owners,
            VersionReplacementFinder replacer,
            Optional<PomVersion> singleVersion)
    {
        BuildLog lg = log().child("commit");
        lg.warn("Begin commit of " + owners.size() + " repository");
        CommitMessage msg = new CommitMessage(BumpVersionMojo.class,
                "Updated versions of " + replacer.changeCount()
                + " projects");
        singleVersion.ifPresent(ver ->
        {
            msg.append("Bump version to " + ver);
        });
        List<Section<?>> generatedSections = new ArrayList<>();
        replacer.collectChanges(msg.sectionFunction(generatedSections));
        generatedSections.forEach(Section::close);
        for (GitCheckout checkout : owners)
        {
            if (isPretend() || checkout.isDirty())
            {
                if (createReleaseBranch && singleFamily && singleVersion
                        .isPresent())
                {
                    String newBranch = "release/" + singleVersion.get();
                    if (!isPretend())
                    {
                        checkout.createAndSwitchToBranch(newBranch,
                                Optional.ofNullable(developmentBranch));
                    }
                }
                if (!isPretend())
                {
                    checkout.addAll();
                    checkout.commit(msg.toString());
                }
                lg.info("Commit " + checkout.name());
            }
        }
    }
}
