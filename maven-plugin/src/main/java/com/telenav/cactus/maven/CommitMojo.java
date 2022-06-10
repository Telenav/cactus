package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "commit", threadSafe = true)
public class CommitMojo extends ScopedCheckoutsMojo
{

    @Parameter(property = "telenav.commit-message", required = true)
    private String message;

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> matched) throws Exception
    {
        List<GitCheckout> checkouts = matched.stream().filter(GitCheckout::hasUncommitedChanges)
                .collect(Collectors.toCollection(ArrayList::new));

        GitCheckout root = tree.root();
        if (isIncludeRoot() && !checkouts.contains(root))
        {
            checkouts.add(root);
        }
        log.info("Begin commit with message '" + message + "'");
        for (GitCheckout at : checkouts)
        {
            log.info("add/commit " + at);
            if (!isPretend())
            {
                at.addAll();
                at.commit(message);
            }
        }
    }
}
