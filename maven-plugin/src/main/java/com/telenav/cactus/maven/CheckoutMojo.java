package com.telenav.cactus.maven;

import com.google.common.base.Strings;
import com.telenav.cactus.maven.Brancher.NonexistentBranchBehavior;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Check out a branch.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "checkout", threadSafe = true)
public class CheckoutMojo extends ScopedCheckoutsMojo
{

    @Parameter(property = "branch", required = true)
    private String branch;

    @Parameter(property = "default-base-branch", required = true)
    private String failoverBaseBranch;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (Strings.isNullOrEmpty(branch))
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        validateBranchName(branch, false);
        validateBranchName(failoverBaseBranch, true);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        if (checkouts.isEmpty())
        {
            log.info("No checkouts matched.");
            return;
        }
        new Brancher(branch, failoverBaseBranch, log, this, isPretend(), NonexistentBranchBehavior.FAIL)
                .updateBranches(checkouts, log, tree);
    }

}