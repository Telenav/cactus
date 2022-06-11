package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Performs a (careful) git pull on any checkouts in the tree that need it,
 * scoped to project family, all, just this checkout or all checkouts with a
 * project of the same group id, using the <code>scope</code> property.
 * <p>
 * If <code>permit-local-modifications</code> is set to true, pulls will be
 * attempted even with modified sources.
 * </p>
 * <p>
 * Checkouts which are in detached-head state (no branch to pull from) are
 * skipped.
 * </p>
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "pull", threadSafe = true)
public class PullMojo extends ScopedCheckoutsMojo
{
    @Parameter(property = "telenav.permit-local-modifications", defaultValue = "true")
    private boolean permitLocalModifications;

    private Scope scope;
    private GitCheckout myCheckout;

    @Override
    protected boolean forbidsLocalModifications()
    {
        return !permitLocalModifications;
    }

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        List<GitCheckout> needingPull = needingPull(checkouts);
        if (needingPull.isEmpty())
        {
            log.info("Nothing to pull. All projects are up to date with remote.");
        } else
        {
            for (GitCheckout checkout : needingPull)
            {
                log.info("Pull " + checkout);
                if (!isPretend())
                {
                    checkout.pull();
                }
            }
        }

    }

    private List<GitCheckout> needingPull(Collection<? extends GitCheckout> cos)
    {
        return cos.stream()
                .filter(co -> isPretend() ? co.needsPull() : co.updateRemoteHeads().needsPull())
                .collect(Collectors.toCollection(() -> new ArrayList<>(cos.size())));
    }
}
