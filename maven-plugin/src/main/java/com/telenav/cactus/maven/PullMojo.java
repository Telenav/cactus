package com.telenav.cactus.maven;

import static com.telenav.cactus.maven.PushMojo.needPull;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        name = "pull", threadSafe = true)
public class PullMojo extends BaseMojo
{

    @Parameter(property = "telenav.scope", defaultValue = "PROJECT_FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.updateRoot", defaultValue = "true")
    private boolean updateRoot;

    @Parameter(property = "telenav.family", defaultValue = "")
    private String family;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    @Parameter(property = "telenav.permitLocalModifications", defaultValue = "true")
    private boolean permitLocalModifications;

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
            Set<GitCheckout> checkouts = PushMojo.checkoutsForScope(scope, tree,
                    myCheckout, updateRoot, family());
            List<GitCheckout> needingPull
                    = sortByDepth(needingPull(checkouts));
            if (needingPull.isEmpty())
            {
                log.info("Nothing to pull.");
            } else
            {
                for (GitCheckout checkout : needingPull)
                {
                    log.info("Pull " + checkout);
                    if (!pretend)
                    {
                        checkout.pull();
                    }
                }
            }
        });
    }

    static List<GitCheckout> needingPull(Collection<? extends GitCheckout> cos)
    {
        return cos.stream()
                .filter(co ->
                {
                    co.updateRemoteHeads();
                    return needPull(co);
                })
                .collect(Collectors.toCollection(() -> new ArrayList<>(cos.size())));
    }

    private static List<GitCheckout> sortByDepth(List<GitCheckout> result)
    {
        Collections.sort(result, (a, b) ->
        {
            int ia = a.checkoutRoot().getNameCount();
            int ib = b.checkoutRoot().getNameCount();
            int sort = Integer.compare(ib, ia);
            if (sort == 0)
            {
                sort = a.checkoutRoot().compareTo(b.checkoutRoot());
            }
            return sort;
        });
        return result;
    }
}
