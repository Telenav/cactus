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
        name = "git-pull-request", threadSafe = true)
public class GitPullRequestMojo extends ScopedCheckoutsMojo
{
    @Parameter(property = "authentication-token", required = true)
    private String authenticationToken;

    @Parameter(property = "title", required = true)
    private String title;

    @Parameter(property = "body", required = true)
    private String body;

    @Override
    protected void onValidateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (authenticationToken.isBlank())
        {
            throw new RuntimeException("Must supply github authentication token");
        }
        if (title.isBlank())
        {
            throw new RuntimeException("Must supply title");
        }
        if (body.isBlank())
        {
            throw new RuntimeException("Must supply body");
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
            checkout.pullRequest(authenticationToken, title, body);
        }
    }
}
