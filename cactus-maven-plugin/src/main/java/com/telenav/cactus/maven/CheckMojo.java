package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.tree.ConsistencyChecker2;
import com.telenav.cactus.maven.tree.Problems;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.scope.ProjectFamily;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Performs a set of (currently slow) sanity checks of project tree state.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "check", threadSafe = true)
public class CheckMojo extends SharedProjectTreeMojo
{
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
     * The set of families to check - if unset, check all of them.
     */
    @Parameter(property = "cactus.families", required = false)
    private String families;

    public CheckMojo()
    {
        super(RunPolicies.FIRST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ConsistencyChecker2 c = new ConsistencyChecker2()
                .activityLogger(log::info);
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
        if (families != null)
        {
            ProjectFamily.fromCommaDelimited(families, () -> null).forEach(
                    fam ->
            {
                c.checkFamily(fam);
            });
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

}
