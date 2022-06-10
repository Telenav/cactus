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
 * Performs a git commit, with the passed <code>commit-message</code> which
 * <b>must</b> be supplied (try enclosing the -D argument entirely on the
 * command-line, e.g. <code>'-Dcommit-message=Some commit message'</code>).
 * <p>
 * The scope for which commits are generated is FAMILY by default, generating
 * commits for all git sub-repositories of the subrepo parent which share a
 * project family (derived from the project's groupId). Passing ALL will change
 * it to any repos containing modified sources). JUST_THIS will commit only the
 * repository that owns the current project.
 * </p>
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

    @Parameter(property = "commit-message", required = true)
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
