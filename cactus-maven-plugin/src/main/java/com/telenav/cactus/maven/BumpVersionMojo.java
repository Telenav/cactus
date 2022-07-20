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

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.commit.CommitMessage;
import com.telenav.cactus.maven.commit.CommitMessage.Section;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.refactoring.SuperpomBumpPolicy;
import com.telenav.cactus.maven.refactoring.VersionMismatchPolicy;
import com.telenav.cactus.maven.refactoring.VersionMismatchPolicyOutcome;
import com.telenav.cactus.maven.refactoring.VersionReplacementFinder;
import com.telenav.cactus.maven.refactoring.VersionUpdateFilter;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.util.EnumMatcher;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.COMMIT_CHANGES;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.DOT;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.MAJOR;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.MINOR;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.NONE;
import static com.telenav.cactus.scope.ProjectFamily.familyOf;
import static com.telenav.cactus.scope.Scope.FAMILY;
import static com.telenav.cactus.scope.Scope.FAMILY_OR_CHILD_FAMILY;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.sort;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Computes the new version of projects in the requested scope and updates pom files and documentation files.
 * <p>
 * If the new version is a release version, the release branch name is automatically computed unless passed explicitly.
 * </p><p>
 * Assumes three-decimal semantic versioning versions, e.g. 1.5.3 corresponding to major-version, minor-version,
 * dot-version.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused") @org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "bump-version", threadSafe = true)
@BaseMojoGoal("bump-version")
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

    private static final SharedDataKey<Object> versionBumpKey
            = SharedDataKey.of("versionBump", Object.class);

    private static final String DEFAULT_RELEASE_BRANCH_PREFIX = "release/";
    /**
     * The magnitude of the decimal to change (subsequent ones will be zeroed). Possible values:
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
     * The kind of change to make to the <i>flavor</i> (represented by a suffix such as "snapshot"). Possible values:
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
     * Defines what to do in the case of a version mismatch, which occurs when the version in a pom the mojo expects to
     * be updating from X to Y is not X. The possible values are:
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
     * By default, this mojo walks the closure of all poms that are affected by the requested change, and bumps versions
     * of any superpoms with updated properties, and then walks through all their children updating their parent
     * versions, so that absolutely everything in the tree expects a consistent set of versions of things.
     * <p>
     * If you want to update one project family _without_ altering any of the others, set this to true and updates to
     * any poms outside the family will be skipped, regardless of the consequences for the buildability of the result.
     * </p>
     */
    @Parameter(property = "cactus.version.single.family")
    boolean singleFamily;

    /**
     * If a superpom is updated, it should get its version bumped (along with all projects that use that superpom as a
     * parent) so all of its children - particularly on other machines not where this code is running, which may have
     * stale local versions of that superpom or its children that would not contain the updated property.
     * <p>
     * This property describes what to do to the version of a superpom in or out of a family being updated, which needs
     * its version bumped because of a property change.
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
     * best, will result in committing changes that may result in things building
     * for you but not for other people until they clean and rebuild everything
     * or download new snapshots. With this option, simply does not make any
     * changes to superpoms unless they are part of the family being updated.
     * </li>
     * </ul>
     */
    @Parameter(property = "cactus.superpom.bump.policy",
               defaultValue = "bump-without-changing-flavor")
    String bumpPolicy;

    /**
     * Explicitly set the version you want to change the family to, overriding the version change magnitude and similar.
     * In general, this is not needed, as the plugin will always compute a correct new version based on what you tell it
     * to do. This property can only be used when updating only a single project or project family - attempting to apply
     * the same version to a bunch of heterogeneous projects is assumed to be operator error and will fail the build
     * (you can always do it one by one if that's really what you want).
     */
    @Parameter(property = "cactus.explicit.version")
    String explicitVersion;

    /**
     * Perform substitutions in documentation files.
     */
    @Parameter(property = "cactus.update.docs", defaultValue = "true")
    boolean updateDocs;

    /**
     * Generate commits with the new version changes for each repository modified.
     */
    @Parameter(property = COMMIT_CHANGES, defaultValue = "false")
    boolean commit;

    /**
     * Create a new release branch, if the version is a release and we are in single-family mode.
     */
    @Parameter(property = "cactus.create.release.branch", defaultValue = "false")
    boolean createReleaseBranch;

    /**
     * Only used when creating a release branch.
     */
    @Parameter(property = "cactus.development.branch", defaultValue = "develop")
    String developmentBranch;

    
    @Parameter(property = "cactus.no.bump.families")
    private String noRevisionFamilies;
    
    /**
     * In the case that multiple families are being updated, but only one should get a higher-than-dot-magnitude update,
     * pass a comma-delimited list of families that should be a dot-magnitude bump regardless of other things.
     */
    @Parameter(property = "cactus.dot.bump.families")
    private String dotRevisionFamilies;

    @Parameter(property = "cactus.minor.bump.families")
    private String minorRevisionFamilies;

    @Parameter(property = "cactus.major.bump.families")
    private String majorRevisionFamilies;

    /**
     * By default, the prefix for a release branch is <code>release/</code>. It can be overridden here, or via the
     * system property releaseBranchPrefix - this is useful when doing release dry-runs where you really do want to
     * create branches and perhaps push them, but not squat the name the eventual real release branch will get.
     */
    @Parameter(property = "cactus.release.branch.prefix")
    String releaseBranchPrefix;

    /**
     * If set, will scan all poms and check with Maven Central (or someplace else) to determine if each pom has already
     * been published in its current version, and if so, if the published pom is identical, and if it has been and they
     * are not, queue it up for a version bump. This is needed when releasing so as not to attempt to publish things
     * that have already been published, or cause newly published libraries to depend the versions expressed in the
     * already published poms when those are not what they were actually built against.
     */
    @Parameter(property = "cactus.bump.published", defaultValue = "true")
    boolean bumpPublished;

    public BumpVersionMojo()
    {
        super(RunPolicies.LAST_CONTAINING_GOAL);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout, ProjectTree tree,
                           List<GitCheckout> checkouts) throws Exception
    {
        if (wasRun())
        {
            log.info("Version bump was already run.");
        }
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
        if (bumpPublished)
        {
            replacer.bumpUnpublishedPoms();
        }
        log.info(
                "Computing changes for " + magnitude() + " " + flavor() + " " + mismatchPolicy());
        if (explicitVersion != null && scope().canBeMultiFamily())
        {
            fail("Cannot use an explicit version with a scope that "
                    + "can match more than one family, such as '" + scope() + "'."
                    + " Only '" + FAMILY + "' or '" + FAMILY_OR_CHILD_FAMILY + "' are "
                    + "legal if you pass a specific version to change to.");
        }
        Map<ProjectFamily, PomVersion> versionForFamily = new HashMap<>();

        if (isVerbose())
        {
            log.info("BumpVersion " + scope() + " " + families() + " for '"
                    + super.families + "' and '" + super.family + "'");
        }

        // Set up version changes for the right things based on the scope:
        switch (scope())
        {
            case JUST_THIS:
                Pom myPom = Pom.from(project.getFile().toPath()).get();

                PomVersion myNewVersion = newVersion(myPom);

                replacer.withSinglePomChange(myPom, myNewVersion);
                versionForFamily.put(familyOf(myPom), myNewVersion);
                break;
            case SAME_GROUP_ID:
                tree.projectsForGroupId(project.getGroupId()).forEach(pom ->
                {
                    PomVersion nv = newVersion(pom);
                    replacer.withSinglePomChange(pom, nv);
                });
                break;
            case FAMILY:
                for (ProjectFamily fam : families())
                {
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
                                            magnitude(fam),
                                            flavor())
                                    .get();
                        }
                        versionForFamily.put(fam, nue);
                        replacer.withFamilyVersionChange(fam,
                                v,
                                nue);
                        if (isVerbose())
                        {
                            log.info(
                                    "Version for " + fam + " is " + v + " -> " + nue);
                        }
                    });
                }
                break;
            case FAMILY_OR_CHILD_FAMILY:
                familyWithChildFamilies(tree).forEach(family ->
                        findVersionOfFamily(tree, family).ifPresent(ffv ->
                        {
                            PomVersion newFamilyVersion = ffv.updatedWith(
                                    magnitude(family),
                                    flavor()).get();
                            versionForFamily.put(family, ffv);
                            replacer.withFamilyVersionChange(family,
                                    ffv,
                                    newFamilyVersion);
                        }));
                break;
            case ALL:
            case ALL_PROJECT_FAMILIES:
                allFamilies(tree).forEach(family ->
                        findVersionOfFamily(tree, family).ifPresent(ffv ->
                        {
                            PomVersion newFamilyVersion = ffv.updatedWith(
                                    magnitude(family),
                                    flavor()).get();

                            versionForFamily.put(family, ffv);
                            replacer.withFamilyVersionChange(family,
                                    ffv,
                                    newFamilyVersion);
                        }));
                break;
            default:
                throw new AssertionError(scope());
        }
        if (isIncludeRoot())
        {
            if (tree.root().isSubmoduleRoot() && tree.root().hasPomInRoot())
            {
                if (isVerbose())
                {
                    log.info("Including root");
                }
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
            else if (isVerbose())
            {
                log.info("NOT including root");
            }
        }
        replacer.pretend(isPretend());
        log.info("Applying changes");
        log.info(replacer.toString());
        Set<Path> rewritten = replacer.go(log::info);

        Rollback rollback = new Rollback().addFileModifications(rewritten);

        rollback.executeWithRollback(ThrowingRunnable.composable(false)
                .andAlways(() ->
                {

                    // Pending:  Construct new MavenProject instances and call
                    // session().setAllProjects() with them, so the build can proceed if
                    // possible?
                    // Use the set of checkouts that contain files that were actually modified,
                    // so we don't generate substitution changes in checkouts we did not
                    // make changes in
                    Set<GitCheckout> owners = GitCheckout.ownersOf(rewritten);

                    if (isVerbose())
                    {
                        log.info("Owners:");
                        owners.forEach(o -> log.info("  * " + o.loggingName()));
                    }

                    Map<GitCheckout, String> releaseBranchNames = new HashMap<>();

                    boolean addOwners = !owners.contains(tree.root()) && tree
                            .root()
                            .isSubmoduleRoot();

                    if (updateDocs)
                    {
                        computeReleaseBranchNames(owners, tree,
                                versionForFamily,
                                releaseBranchNames, log);

                        runSubstitutions(log, project, myCheckout, tree,
                                new ArrayList<>(owners), releaseBranchNames,
                                rewritten::add);
                    }
                    if (commit)
                    {
                        log.info("Commit is true.");
                        if (createReleaseBranch)
                        {
                            // Ensure we affect the root checkout too.
                            owners.add(tree.root());
                            log.info(
                                    "Create release branch in " + owners.size() + " repositories");
                            if (releaseBranchNames.isEmpty())
                            {
                                computeReleaseBranchNames(owners, tree,
                                        versionForFamily,
                                        releaseBranchNames, log);
                            }
                            if (addOwners && !releaseBranchNames.isEmpty())
                            {
                                releaseBranchNames.put(tree.root(), longest(
                                        releaseBranchNames));
                            }

                            // XXX check that the branch does not exist
                            // If it does, roll back everything
                            // Or optionally, delete it if it does?
                        }
                        generateCommit(owners, replacer, releaseBranchNames,
                                rollback, tree);
                    }
                    // Ensures the tree's cache is cleared at the tail even if this
                    // throws
                }).andAlwaysRun(tree::invalidateCache));
    }

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        super.onValidateParameters(log, project);
        if (createReleaseBranch)
        {
            commit = true; // implicit
        }

        switch (scope())
        {
            case FAMILY:
                break;
            case JUST_THIS:
                if (createReleaseBranch)
                {
                    fail("Cannot use createReleaseBranch when bumping a single project.");
                }
            case ALL:
            case ALL_PROJECT_FAMILIES:
            case SAME_GROUP_ID:
            case FAMILY_OR_CHILD_FAMILY:
                break;
            default:
                throw new AssertionError(scope());
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
            if (families().size() > 1) {
                fail("Cannot use an explicit version when updating more than one family.");
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
                project.getProperties().put("cactus.version.change.description",
                        ProjectFamily.fromGroupId(project.getGroupId()) + " " + vc));

        log.info("Bump version of " + project.getGroupId() + ":" + project
                .getArtifactId() + " from "
                + oldVersion + " to " + updatedVersion);

        super.newVersion = updatedVersion.text();
        if (super.newBranchName == null && updatedVersion.flavor() == VersionFlavor.RELEASE)
        {
            super.newBranchName = releaseBranchPrefix() + updatedVersion;
        }
        checkFamilyParameters();
    }

    private static String longest(Map<GitCheckout, String> m)
    {
        assert !m.isEmpty();
        List<String> l = new ArrayList<>(m.values());
        // Assume the longest branch name is the aggregate one
        l.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return l.get(0);
    }

    VersionFlavorChange flavor()
    {
        return FLAVOR_MATCHER.matchOrThrow(versionFlavor);
    }

    VersionChangeMagnitude magnitude()
    {
        Optional<VersionChangeMagnitude> mag = MAGNITUDE_MATCHER.match(
                versionChangeMagnitude);
        if (mag.isEmpty())
        {
            throw new IllegalArgumentException(
                    "Unrecognized magnitude change flavor '" + versionChangeMagnitude + "'");
        }
        return mag.get();
    }
    
    private Set<ProjectFamily> projectFamiliesFrom(String familySet) {
        if (familySet == null || familySet.isBlank()) {
            return emptySet();
        }
        // Elide quotes injected by bad shell quoting:
        familySet = familySet.replaceAll("\"", "").replaceAll("'", "");
        Set<ProjectFamily> result = new HashSet<>(5);
        for (String s : familySet.split("[, ]")) {
            s = s.trim();
            if (!s.isEmpty()) {
                result.add(ProjectFamily.named(s));
            }
        }
        return result;
    }
    
    private Set<ProjectFamily> noRevisionFamilies() {
        return projectFamiliesFrom(noRevisionFamilies);
    }
    private Set<ProjectFamily> dotRevisionFamilies() {
        return projectFamiliesFrom(dotRevisionFamilies);
    }
    
    private Set<ProjectFamily> minorRevisionFamilies() {
        return projectFamiliesFrom(minorRevisionFamilies);
    }
    
    private Set<ProjectFamily> majorRevisionFamilies() {
        return projectFamiliesFrom(majorRevisionFamilies);
    }
    
    private static Set<?> combine(Set<?>... all) {
        Set<Object> result = new HashSet<>();
        for (Set<?> s : all) {
            result.addAll(s);
        }
        return result;
    }
    
    private void checkFamilyParameters() {
        Set<ProjectFamily> dot = dotRevisionFamilies();
        Set<ProjectFamily> minor = minorRevisionFamilies();
        Set<ProjectFamily> major = majorRevisionFamilies();
        Set<ProjectFamily> none = noRevisionFamilies();
        Set<?> all = combine(dot, minor, major, none);
        if (all.isEmpty()) {
            return;
        }
        if (all.size() != dot.size() + minor.size() + major.size() + none.size()) {
            fail("Contradictory revision changes specified"
                    + " - at least one family is in more than one category.\n"
                    + "\tDot: " + dot + "\nMinor: " + minor + "\nMajor: " + major + "\nNone: " + none);
        }
        Set<ProjectFamily> expectedFamilies = families();
        if (!expectedFamilies.containsAll(all)) {
            all.removeAll(families());
            StringBuilder msg = new StringBuilder("Some families are slated for "
                    + "a none, dot, minor or major version bump, but are not actually in "
                    + "the set of cactus.families:");
            for (Object fam : all) {
                msg.append("\n  * ").append(fam);
            }
            msg.append("\nFamilies specified:");
            for (ProjectFamily fam : expectedFamilies) {
                msg.append("\n  * ").append(fam);
            }
            fail(msg.toString());
        }
    }

    VersionChangeMagnitude magnitude(ProjectFamily family)
    {
        if (noRevisionFamilies().contains(family))
        {
            return NONE;
        }
        else if (dotRevisionFamilies().contains(family))
        {
            return DOT;
        }
        else if (minorRevisionFamilies().contains(family))
        {
            return MINOR;
        } else if (majorRevisionFamilies().contains(family))
        {
            return MAJOR;
        }
        return magnitude();
    }

    VersionMismatchPolicy mismatchPolicy()
    {
        return MISMATCH_MATCHER.matchOrThrow(versionMismatchPolicy);
    }

    SuperpomBumpPolicy superpomBumpPolicy()
    {
        return SUPERPOM_POLICY_MATCHER.matchOrThrow(this.bumpPolicy);
    }

    private Set<ProjectFamily> allFamilies(ProjectTree tree)
    {
        Set<ProjectFamily> allFamilies = new HashSet<>();
        tree.allProjects().forEach(pom -> allFamilies.add(ProjectFamily
                .familyOf(pom.groupId())));
        return allFamilies;
    }

    @SuppressWarnings("SameParameterValue")
    private void checkCheckoutStates(String messageHead,
                                     ProjectTree tree, List<GitCheckout> checkouts)
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
            else if (tree.isDirty(co))
            {
                if (commit)
                {
                    failMessage.append('\n');
                    failMessage.append(nm).append(
                            " has local modifications, and a commit has been requested");
                }
            }
            else
            {
                Optional<Branches.Branch> br = tree.branches(co)
                        .currentBranch();
                if (br.isEmpty())
                {
                    failMessage.append('\n')
                            .append(nm)
                            .append("Is not on the development branch '")
                            .append(developmentBranch)
                            .append("' but on '")
                            .append(br.get());
                }
            }
        }
        if (failMessage.length() > 0)
        {
            failMessage.insert(0, messageHead);
            fail(failMessage.toString());
        }
    }
    
    private void computeReleaseBranchName(GitCheckout co, ProjectTree tree,
                                          Map<ProjectFamily, PomVersion> familyVersion,
                                          Map<GitCheckout, String> releaseBranchNames, BuildLog log1)
    {
        Set<ProjectFamily> familiesHere = new TreeSet<>();
        boolean isTreeRoot = co.equals(tree.root());
        if (isTreeRoot)
        {
            familiesHere.addAll(familyVersion.keySet());
        }
        else
        {
            tree.projectsWithin(co).forEach(prj -> familiesHere.add(familyOf(prj)));
        }
        familiesHere.retainAll(familyVersion.keySet());
        if (!familiesHere.isEmpty())
        {
            String prefix = releaseBranchPrefix();
            StringBuilder sb = new StringBuilder(prefix);
            if (familiesHere.size() == 1)
            {
                sb.append(familyVersion.get(familiesHere.iterator()
                        .next()));
            }
            else
            {
                for (ProjectFamily pf : familiesHere)
                {
                    if (sb.length() > prefix.length())
                    {
                        sb.append('_');
                    }
                    PomVersion ver = familyVersion.get(pf);
                    sb.append(pf).append('-').append(ver);
                }
            }
            releaseBranchNames.put(co, sb.toString());
            String logName = (co.name().isEmpty()
                    ? "(root)"
                    : co.name());
            log1.info("Release branch for " + logName
                    + " is " + sb);
        }
    }

    private void computeReleaseBranchNames(Set<GitCheckout> owners,
                                           ProjectTree tree, Map<ProjectFamily, PomVersion> familyVersion,
                                           Map<GitCheckout, String> releaseBranchNames, BuildLog log1)
    {
        for (GitCheckout co : owners)
        {
            computeReleaseBranchName(co, tree, familyVersion, releaseBranchNames,
                    log1);
        }
        if (isVerbose())
        {
            log.info("Have " + releaseBranchNames.size() + " release branches:");
            releaseBranchNames.forEach((k, v) -> log.info("  * " + k
                    .loggingName() + " -> " + v));
        }
    }

    private Set<ProjectFamily> familyWithChildFamilies(ProjectTree tree)
    {
        Set<ProjectFamily> fams = families();
        Set<ProjectFamily> relatives = new HashSet<>();
        for (ProjectFamily fam : fams)
        {
            tree.allProjects().forEach(pom
                    -> fam.ifParentFamilyOf(pom.groupId(),
                    () -> relatives.add(ProjectFamily
                            .familyOf(pom.groupId())))
            );
            relatives.add(fam);
        }
        return relatives;
    }

    private VersionUpdateFilter filter()
    {
        if (singleFamily)
        {
            Set<ProjectFamily> all = families();
            if (all.isEmpty())
            {
                all = singleton(ProjectFamily.familyOf(GroupId.of(project()
                        .getGroupId())));
            }
            return VersionUpdateFilter.withinFamilyOrParentFamily(
                    all.iterator().next());
        }
        else
        {
            return VersionUpdateFilter.DEFAULT;
        }
    }

    private Optional<PomVersion> findVersionOfFamily(ProjectTree tree,
                                                     ProjectFamily family)
    {
        return family.probableFamilyVersion(tree.allProjects());
    }

    private void generateCommit(Set<GitCheckout> ownerSet,
                                VersionReplacementFinder replacer,
                                Map<GitCheckout, String> branchNameForCheckout, Rollback rollback, ProjectTree tree)
            throws Exception
    {
        if (ownerSet.isEmpty())
        {
            return;
        }
        List<GitCheckout> owners = GitCheckout.depthFirstSort(ownerSet);
        
        BuildLog lg = log().child("commit");
        lg.warn("Begin commit of " + owners.size() + " repositories");
        CommitMessage msg = new CommitMessage(BumpVersionMojo.class,
                "Updated versions in " + replacer.changeCount()
                        + " projects");

        // Populate the commit message with details about exactly
        // what was changed where
        replacer.collectChanges(msg);

        // Populate the commit message with branch information
        populateCommitMessage(msg, owners, branchNameForCheckout);

        // Ensure we don't commit the root checkout twice - the second time
        // will fail with no changes.
        Set<GitCheckout> committed = new HashSet<>();
        
        for (GitCheckout checkout : owners)
        {
            if (createReleaseBranch)
            {
                String branchName = branchNameForCheckout.get(checkout);
                if (branchName != null)
                {
                    branchOneCheckout(branchName, checkout, rollback);
                }
                else
                {
                    log.info("No branch name was computed for " + checkout
                            .checkoutRoot());
                }
            }

            if (!isPretend())
            {
                checkout.addAll();
                checkout.commit(msg.toString());
            }
            committed.add(checkout);
            lg.info("Commited " + checkout.name());
        }
        if (!owners.isEmpty() 
                && createReleaseBranch 
                && isIncludeRoot()
                && !committed.contains(tree.root())
                && tree.root().isSubmoduleRoot())
        {
            createRootCheckout(branchNameForCheckout, tree, msg, rollback);
        }
    }

    private void branchOneCheckout(String branchName, GitCheckout checkout,
            Rollback rollback) throws Exception
    {
        log().info(
                "Create and switch to " + branchName + " in "
                        + " based on " + developmentBranch + " in "
                        + checkout);
        
        ifNotPretending(() ->
        {
            checkout.createAndSwitchToBranch(branchName,
                    Optional.empty());
            rollback.addRollbackTask(() ->
            {
                log().info(
                        "Rollback branch creation for " + branchName + " in " + checkout);
                checkout.deleteBranch(branchName,
                        developmentBranch,
                        true);
            });
        });
    }

    private void createRootCheckout(
            Map<GitCheckout, String> branchNameForCheckout, ProjectTree tree,
            CommitMessage msg, Rollback rollback)
    {
        String bestBranch = branchNameForCheckout.getOrDefault(tree.root(), longest(branchNameForCheckout));
        log.info("Create root checkout commit in " + tree.root().checkoutRoot());
        if (!isPretend())
        {
            tree.root()
                    .createAndSwitchToBranch(bestBranch, Optional.empty());
            tree.root().addAll();
            tree.root().commit(msg.toString());
            rollback.addRollbackTask(() ->
                    tree.root().deleteBranch(bestBranch, this.developmentBranch,
                            true));
        }
    }

    private void populateCommitMessage(CommitMessage msg,
            List<GitCheckout> owners,
            Map<GitCheckout, String> branchNameForCheckout)
    {
        // Alpha sort is more friendly for a commit message than
        // depth-first, which is what we need for doing the actual
        // committing
        List<GitCheckout> sortedByName = new ArrayList<>(owners);
        sort(sortedByName, (a, b) -> {
            return a.loggingName().compareTo(b.loggingName());
        });
        // Populate the commit message
        try (Section<CommitMessage> branchesSection = msg.section("Branches"))
        {
            for (GitCheckout checkout : sortedByName)
            {
                if (createReleaseBranch)
                {
                    String branchName = branchNameForCheckout.get(checkout);
                    if (branchName != null)
                    {
                        branchesSection.bulletPoint(
                                "`" + checkout.loggingName() + "` - " + branchName);
                    }
                }
            }
        }
    }

    private PomVersion newVersion(Pom pom)
    {
        if (explicitVersion != null && families().iterator().next().equals(
                familyOf(pom)))
        {
            return PomVersion.of(explicitVersion);
        }
        else if (explicitVersion != null)
        {
            throw new IllegalStateException(
                    "Explicit version can only be used when altering a "
                            + " SINGLE project family, but have family " + families() + " plus "
                            + familyOf(pom) + " from " + pom);
        }

        VersionChangeMagnitude mag = magnitude(familyOf(pom));
        VersionFlavorChange flavor = flavor();
        PomVersion oldVersion = pom.version();
        return oldVersion.updatedWith(mag, flavor)
                .orElseThrow(
                        () -> new IllegalStateException("Applying " + mag + " "
                                + "+ " + flavor + " to version "
                                + oldVersion + " does not change anything"));
    }

    private String releaseBranchPrefix()
    {
        if (releaseBranchPrefix != null && !releaseBranchPrefix.isBlank())
        {
            String result = releaseBranchPrefix.trim();
            if (result.charAt(result.length() - 1) != '/')
            {
                result += "/";
            }
            return result;
        }
        String prop = System.getProperty("releaseBranchPrefix");
        if (prop != null && !prop.isBlank())
        {
            String result = prop.trim();
            if (result.charAt(result.length() - 1) != '/')
            {
                result += "/";
            }
            return result;
        }
        return DEFAULT_RELEASE_BRANCH_PREFIX;
    }

    private void runSubstitutions(BuildLog log, MavenProject project,
                                  GitCheckout myCheckout, ProjectTree tree,
                                  List<GitCheckout> checkouts,
                                  Map<GitCheckout, String> releaseBranchNames,
                                  Consumer<Path> collector) throws Exception
    {
        super.executeCollectingChangedFiles(log, project, myCheckout, tree,
                checkouts, releaseBranchNames, collector);
    }

    private boolean wasRun()
    {
        synchronized (BumpVersionMojo.class)
        {
            Optional<Object> opt = sharedData().get(versionBumpKey);
            if (opt.isEmpty())
            {
                sharedData().put(versionBumpKey, new Object());
                return false;
            }
            return true;
        }
    }
}
