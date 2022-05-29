package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.maven.plugin.MojoExecutionException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "commit", threadSafe = true)
public class CommitMojo extends BaseMojo
{

    @Parameter(property = "telenav.scope", defaultValue = "PROJECT_FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.updateRoot", defaultValue = "true")
    private boolean updateRoot;

    @Parameter(property = "telenav.family", defaultValue = "")
    private String family;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    @Parameter(property = "telenav.commitMessage", required = true)
    private String message;

    private Scope scope;
    private GitCheckout myCheckout;

    @Override
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        super.validateParameters(log, project);
        scope = Scope.find(scopeProperty);
        Optional<GitCheckout> checkout = GitCheckout.repository(project.getBasedir());
        if (!checkout.isPresent())
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        myCheckout = checkout.get();
        if (message == null)
        {
            throw new MojoExecutionException("Commit message not set");
        }
    }

    @Override
    protected boolean isOncePerSession()
    {
        return true;
    }

    private String family()
    {
        return this.family == null || this.family.isEmpty()
                ? project().getGroupId()
                : this.family;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ProjectTree.from(project).ifPresent(tree ->
        {
            List<GitCheckout> checkouts = PushMojo.checkoutsForScope(scope, tree,
                    myCheckout, updateRoot, family())
                    .stream()
                    .filter(GitCheckout::hasUncommitedChanges)
                    .collect(Collectors.toCollection(ArrayList::new));
            GitCheckout root = tree.root();
            if (updateRoot && !checkouts.contains(root))
            {
                checkouts.add(root);
            }
            Collections.sort(checkouts, (a, b) ->
            {
                int result = Integer.compare(b.checkoutRoot().getNameCount(), a.checkoutRoot().getNameCount());
                if (result == 0)
                {
                    result = a.checkoutRoot().compareTo(b.checkoutRoot());
                }
                return result;
            });
            log.info("Begin commit with message '" + message + "'");
            for (GitCheckout co : checkouts)
            {
                log.info("add/commit " + co);
                if (!pretend)
                {
                    co.addAll();
                    co.commit(message);
                }
            }
        });
    }
}
