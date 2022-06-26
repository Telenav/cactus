package com.telenav.cactus.maven;

import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionFlavor;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.scope.ProjectFamily;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.util.EnumMatcher;
import com.telenav.cactus.maven.versions.VersionMismatchPolicy;
import com.telenav.cactus.maven.versions.VersionMismatchPolicyOutcome;
import com.telenav.cactus.maven.versions.VersionReplacementFinder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

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
        defaultPhase = LifecyclePhase.VERIFY,
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

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        super.onValidateParameters(log, project);
        VersionChangeMagnitude mag = magnitude();
        VersionFlavorChange flavor = flavor();

        if (mag.isNone() && flavor.isNone())
        {
            fail("Nothing to do for " + mag + " " + flavor);
        }
        PomVersion oldVersion = PomVersion.of(project.getVersion());
        PomVersion updatedVersion = oldVersion.updatedWith(mag, flavor)
                .orElseThrow(
                        () -> new MojoExecutionException("Applying " + mag + " "
                                + "+ " + flavor + " to version "
                                + oldVersion + " does not change anything"));

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
        VersionChangeMagnitude mag = magnitude();
        VersionFlavorChange flavor = flavor();
        PomVersion oldVersion = pom.rawVersion();
        PomVersion updatedVersion = oldVersion.updatedWith(mag, flavor)
                .orElseThrow(
                        () -> new IllegalStateException("Applying " + mag + " "
                                + "+ " + flavor + " to version "
                                + oldVersion + " does not change anything"));
        return updatedVersion;
    }

    private PomVersion findVersionOfFamily(ProjectTree tree)
    {
        return findVersionOfFamily(tree, projectFamily());
    }

    private PomVersion findVersionOfFamily(ProjectTree tree,
            ProjectFamily family)
    {
        // Since we have parent-of-parent poms that may have different
        // versions, simply select the most represented version
        Map<PomVersion, Integer> counts = new HashMap<>();
        tree.projectsForFamily(family).forEach(pom ->
        {
            counts.compute(pom.rawVersion(), (k, old) ->
            {
                if (old == null)
                {
                    old = 1;
                }
                else
                {
                    old = old + 1;
                }
                return old;
            });
        });
        if (counts.isEmpty())
        {
            throw new IllegalArgumentException(
                    "No projects in " + projectFamily() + " found");
        }
        List<Map.Entry<PomVersion, Integer>> entries = new ArrayList<>(counts
                .entrySet());
        Collections.sort(entries, (a, b) ->
        {
            return b.getValue().compareTo(a.getValue());
        });
        return entries.get(0).getKey();
    }

    private Set<ProjectFamily> allFamilies(ProjectTree tree)
    {
        Set<ProjectFamily> families = new HashSet<>();
        tree.allProjects().forEach(pom -> families.add(ProjectFamily
                .fromGroupId(pom.groupId())));
        return families;
    }

    private Set<ProjectFamily> familyWithChildFamilies(ProjectTree tree)
    {
        ProjectFamily fam = projectFamily();
        Set<ProjectFamily> relatives = new HashSet<>();
        tree.allProjects().forEach(pom
                -> fam.ifParentFamilyOf(pom.groupId(),
                        () -> relatives.add(ProjectFamily
                                .fromGroupId(pom.groupId())))
        );
        relatives.add(fam);
        return relatives;
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        log.info("Building index of all pom.xmls");
        // The thing that will rewrite the pom files
        VersionReplacementFinder replacer
                = new VersionReplacementFinder(new Poms(tree.allProjects()))
                        .withVersionMismatchPolicy(
                                mismatchPolicy());
        log.info("Computing changes");
        // Set up version changes for the right things based on the scope:
        switch (scope())
        {
            case JUST_THIS:
                Pom myPom = Pom.from(project.getFile().toPath()).get();
                replacer.withSinglePomChange(ArtifactId.of(project
                        .getArtifactId()),
                        GroupId.of(project.getGroupId()),
                        myPom.rawVersion());
                break;
            case SAME_GROUP_ID:
                tree.projectsForGroupId(project.getGroupId()).forEach(pom ->
                {
                    replacer.withSinglePomChange(pom, newVersion(pom));
                });
                break;
            case FAMILY:
                PomVersion v = findVersionOfFamily(tree);
                replacer.withFamilyVersionChange(projectFamily(),
                        v,
                        v.updatedWith(
                                magnitude(),
                                flavor())
                                .get());
                break;
            case FAMILY_OR_CHILD_FAMILY:
                familyWithChildFamilies(tree).forEach(family ->
                {
                    PomVersion familyVersion = findVersionOfFamily(tree, family);
                    replacer.withFamilyVersionChange(family,
                            familyVersion,
                            familyVersion
                                    .updatedWith(
                                            magnitude(),
                                            flavor())
                                    .get());
                });
                break;
            case ALL:
            case ALL_PROJECT_FAMILIES:
                allFamilies(tree).forEach(family ->
                {
                    PomVersion familyVersion = findVersionOfFamily(tree, family);
                    replacer.withFamilyVersionChange(family,
                            familyVersion,
                            familyVersion
                                    .updatedWith(
                                            magnitude(),
                                            flavor())
                                    .get());
                });
                break;
            default:
                throw new AssertionError(scope());
        }
        if (isIncludeRoot())
        {
            System.out.println("INCLUDE ROOT");
            if (tree.root().isSubmoduleRoot() && tree.root().hasPomInRoot())
            {
                System.out.println("  HAVE POM IN ROOT");
                tree.projectOf(tree.root().checkoutRoot().resolve("pom.xml"))
                        .ifPresent(rootPom ->
                        {
                            System.out.println("ADD UDPATE FOR ROOT POM");
                            PomVersion newRootVersion = rootPom.rawVersion()
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
        // session().setAllProjects() with them.
//        session().
        log.info("Done");

        runSubstitutions(log, project, myCheckout, tree, checkouts);
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

    VersionFlavorChange flavor()
    {
        Optional<VersionFlavorChange> chg = FLAVOR_MATCHER.match(versionFlavor);
        if (!chg.isPresent())
        {
            throw new IllegalStateException(
                    "Unrecognized version change flavor '" + versionFlavor + "'");
        }
        return chg.get();
    }

    VersionMismatchPolicy mismatchPolicy()
    {
        Optional<VersionMismatchPolicyOutcome> chg = MISMATCH_MATCHER.match(
                versionMismatchPolicy);
        if (!chg.isPresent())
        {
            throw new IllegalStateException(
                    "Unrecognized version mismatch policy '"
                    + versionMismatchPolicy + "'");
        }
        return chg.get();
    }
}
