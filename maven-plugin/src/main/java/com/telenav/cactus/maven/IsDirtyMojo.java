package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "is-dirty", threadSafe = true)
public class IsDirtyMojo extends BaseMojo
{
    @Parameter(property = "telenav.scope", defaultValue = "FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.update-root", defaultValue = "true")
    private boolean updateRoot;

    @Parameter(property = "telenav.family", defaultValue = "")
    private String family;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

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

            var dirty = false;
            for (var checkout : checkouts)
            {
                if (!pretend)
                {
                    if (checkout.isDirty())
                    {
                        dirty = true;
                        log.info("* " + checkout);
                    }
                }
            }

            if (!dirty)
            {
                log.info("\nClean\n\n");
            }
        });
    }
}
