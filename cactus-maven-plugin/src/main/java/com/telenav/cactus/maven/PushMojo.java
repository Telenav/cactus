package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.git.NeedPushResult;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Perform a git push in any projects that need it, scoped by the
 * <code>scope</code> property to family, all, just-this, etc.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "push", threadSafe = true)
public class PushMojo extends ScopedCheckoutsMojo
{
    static Map<GitCheckout, NeedPushResult> collectPushKinds(Collection<? extends GitCheckout> checkouts)
    {
        Map<GitCheckout, NeedPushResult> result = new HashMap<>();
        for (GitCheckout co : checkouts)
        {
            NeedPushResult res = co.needsPush();
            if (res.canBePushed())
            {
                result.put(co, res);
            }
        }
        return result;
    }

    static boolean needPull(GitCheckout checkout)
    {
        return checkout.mergeBase().map((String mergeBase) ->
                checkout.remoteHead().map((String remoteHead) ->
                        checkout.head().equals(mergeBase)).orElse(false)).orElse(false);
    }

    @Parameter(property = "telenav.permit-local-modifications", defaultValue = "true",
               name = "permit-local-modifications")
    private boolean permitLocalModifications;

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
                           ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        // Depth first sort, so we process the submodule root last, in
        // case commits to child modules put it into the dirty state.
        List<Map.Entry<GitCheckout, NeedPushResult>> needingPush
                = GitCheckout.depthFirstSort(collectPushKinds(checkouts));
        if (needingPush.isEmpty())
        {
            log.info("No projects needing push in " + needingPush);
        }
        else
        {
            pullIfNeededAndPush(log, project, needingPush);
        }
    }

    @Override
    protected boolean forbidsLocalModifications()
    {
        return !permitLocalModifications;
    }

    private Set<GitCheckout> checkNeedPull(List<Map.Entry<GitCheckout, NeedPushResult>> needingPush, BuildLog log)
    {
        Set<GitCheckout> needingPull = new LinkedHashSet<>();
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            log.debug("Update remote heads: " + checkout.name());
            if (!isPretend())
            {
                checkout.updateRemoteHeads();
            }
            if (!co.getValue().needCreateBranch() && checkout.needsPull())
            {
                log.debug("Needs pull: " + checkout.name());
                needingPull.add(checkout);
            }
        }
        return needingPull;
    }

    private void pull(Set<GitCheckout> needingPull, BuildLog log)
    {
        if (!needingPull.isEmpty())
        {
            log.warn("Needing pull:");
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

    private void pullIfNeededAndPush(BuildLog log, MavenProject project,
                                     List<Map.Entry<GitCheckout, NeedPushResult>> needingPush)
    {
        Set<GitCheckout> needingPull = checkNeedPull(needingPush, log.child("checkNeedPull"));
        pull(needingPull, log.child("pull"));
        push(needingPush, log.child("push"));
    }

    private void push(List<Map.Entry<GitCheckout, NeedPushResult>> needingPush, BuildLog log)
    {
        log.warn("Begin push.");
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            if (co.getValue().needCreateBranch())
            {
                log.info("Push creating branch: " + checkout);
                if (!isPretend())
                {
                    checkout.pushCreatingBranch();
                }
            }
            else
            {
                log.info("Push: " + checkout);
                if (!isPretend())
                {
                    checkout.push();
                }
            }
        }
    }
}