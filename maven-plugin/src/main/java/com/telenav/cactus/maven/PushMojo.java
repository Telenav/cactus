package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.git.NeedPushResult;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
        name = "push", threadSafe = true)
public class PushMojo extends BaseMojo
{

    @Parameter(property = "telenav.scope", defaultValue = "PROJECT_FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.updateRoot", defaultValue = "true")
    private boolean updateAndPushRoot;

    @Parameter(property = "telenav.family", defaultValue = "")
    private String family;

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
        return this.family == null || this.family.isEmpty() ? project().getGroupId() : this.family;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ProjectTree.from(project).ifPresent(tree ->
        {
            Set<GitCheckout> checkouts;
            switch (scope)
            {
                case PROJECT_FAMILY:
                    checkouts = tree.checkoutsContainingGroupId(family());
                    break;
                case PROJECT:
                    checkouts = Collections.singleton(myCheckout);
                    break;
                case EVERYTHING:
                    checkouts = new HashSet<>(tree.allCheckouts());
                    checkouts.addAll(tree.nonMavenCheckouts());
                    break;
                default:
                    throw new AssertionError(scope);
            }
            if (!updateAndPushRoot)
            {
                myCheckout.submoduleRoot().ifPresent(checkouts::remove);
            } else
            {
                // don't generate a push of _just_ the root checkout
                if (!checkouts.isEmpty())
                {
                    myCheckout.submoduleRoot().ifPresent(checkouts::add);
                }
            }
//            List<GitCheckout> needingPush = checkouts.stream().filter(checkout -> !checkout.isInSyncWithRemoteHead())
//                    .collect(Collectors.toCollection(ArrayList::new));
//            List<GitCheckout> needingPush = checkouts.stream().filter(checkout -> !checkout.needsPush().canBePushed())
//                    .collect(Collectors.toCollection(ArrayList::new));
            Map<GitCheckout, NeedPushResult> pushTypeForCheckout = new HashMap<>();
            for (GitCheckout co : checkouts)
            {
                NeedPushResult res = co.needsPush();
                System.out.println(" :::" + co.checkoutRoot().getFileName() + " " + res);
                if (res.canBePushed())
                {
                    pushTypeForCheckout.put(co, res);
                }
            }

            List<Map.Entry<GitCheckout, NeedPushResult>> needingPush = new ArrayList<>(pushTypeForCheckout.entrySet());
            // In case we have nested submodules, sort so we push deepest first
            Collections.sort(needingPush, (a, b) ->
            {
                int adepth = a.getKey().checkoutRoot().getNameCount();
                int bdepth = b.getKey().checkoutRoot().getNameCount();
                int result = Integer.compare(bdepth, adepth);
                if (result == 0)
                {
                    result = a.getKey().checkoutRoot().getFileName().compareTo(b.getKey().checkoutRoot().getFileName());
                }
                return result;
            });
            if (needingPush.isEmpty())
            {
                log.info("No projects needing push in " + needingPush);
            } else
            {
                pullIfNeededAndPush(log, project, tree, needingPush);
            }
        });
    }

    private void pullIfNeededAndPush(BuildLog log, MavenProject project, ProjectTree tree, List<Map.Entry<GitCheckout, NeedPushResult>> needingPush)
    {
        log.warn("Updating remote heads");
        Set<GitCheckout> needingPull = new LinkedHashSet<>();
        Set<GitCheckout> nowUpToDate = new HashSet<>();
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            checkout.updateRemoteHeads();
            if (!co.getValue().needCreateBranch())
            {
                checkout.mergeBase().ifPresent(mergeBase ->
                {
                    checkout.remoteHead().ifPresent(remoteHead ->
                    {
                        String head = checkout.head();
                        if (head.equals(mergeBase))
                        {
                            needingPull.add(checkout);
                        } else if (head.equals(remoteHead))
                        {
                            nowUpToDate.add(checkout);
                        }
                    });
                });
            }
        }

        if (!needingPull.isEmpty())
        {
            log.warn("Needing pull:");
            for (GitCheckout checkout : needingPull)
            {
                log.info("Pull " + checkout);
                checkout.pull();
            }
        }

        log.warn("Pushing: ");
        for (Map.Entry<GitCheckout, NeedPushResult> co : needingPush)
        {
            GitCheckout checkout = co.getKey();
            if (nowUpToDate.contains(checkout))
            {
                continue;
            }
            System.out.println("  * " + co.getKey().checkoutRoot());
            if (co.getValue().needCreateBranch())
            {
                log.info("Push creating branch: " + checkout);
                checkout.pushCreatingBranch();
            } else
            {
                log.info("Push: " + checkout);
                checkout.push();
            }
        }
    }

    public enum Scope
    {
        PROJECT,
        PROJECT_FAMILY,
        EVERYTHING;

        public static Scope find(String prop) throws MojoExecutionException
        {
            if (prop == null)
            {
                return PROJECT_FAMILY;
            }
            for (Scope scope : values())
            {
                if (scope.name().equalsIgnoreCase(prop))
                {
                    return scope;
                }
            }
            String msg = "Unknown scope " + prop
                    + " is not one of " + Arrays.toString(values());
            throw new MojoExecutionException(Scope.class, msg, msg);
        }
    }
}
