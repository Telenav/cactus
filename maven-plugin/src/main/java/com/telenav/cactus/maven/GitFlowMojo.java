package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Starts and finishes branches accoding to git flow conventions.
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
        name = "git-flow", threadSafe = true)
public class GitFlowMojo extends ScopedCheckoutsMojo
{
    @Parameter(property = "operation", required = true)
    private String operation;

    @Parameter(property = "branch-type", required = true)
    private String branchType;

    @Parameter(property = "branch", required = true)
    private String branch;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        validateBranchName(branch, false);
        if (!branchType.matches("feature|hotfix|release"))
        {
            throw new RuntimeException("Branch type must be one of: feature, hotfix, or release");
        }
        if (!operation.matches("start|finish"))
        {
            throw new RuntimeException("Operation must be either start or finish");
        }
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

        for (var checkout : checkouts)
        {
            checkout.flow(operation, branchType, branch);
        }
    }
}
