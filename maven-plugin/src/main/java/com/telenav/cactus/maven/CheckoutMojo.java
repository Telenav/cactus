package com.telenav.cactus.maven;

import com.google.common.base.Strings;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.telenav.cactus.maven.PushMojo.needPull;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "checkout", threadSafe = true)
public class CheckoutMojo extends BaseMojo
{
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

    @Parameter(property = "telenav.scope", defaultValue = "FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.update-root", defaultValue = "true")
    private boolean updateRoot;

    @Parameter(property = "telenav.family")
    private String family;

    @Parameter(property = "telenav.branch")
    private String branch;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    @Parameter(property = "telenav.permit-local-modifications", defaultValue = "true")
    private boolean permitLocalModifications;

    private Scope scope;

    private GitCheckout myCheckout;

    @Override
    protected boolean isOncePerSession()
    {
        return true;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (Strings.isNullOrEmpty(branch))
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        ProjectTree.from(project).ifPresent(tree ->
        {
            for (var checkout : PushMojo.checkoutsForScope(scope, tree, myCheckout, updateRoot, family()))
            {
                log.info("Checkout " + checkout + " [" + branch + "]");
                if (!pretend)
                {
                    checkout.switchToBranch(branch);
                }
            }
        });
    }

    @Override
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        super.validateParameters(log, project);
        scope = Scope.find(scopeProperty);
        Optional<GitCheckout> checkout = GitCheckout.repository(project.getBasedir());
        if (checkout.isEmpty())
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        myCheckout = checkout.get();
    }

    private String family()
    {
        return this.family == null || this.family.isEmpty()
                ? project().getGroupId()
                : this.family;
    }
}
