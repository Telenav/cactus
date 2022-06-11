package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.util.List;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Tests if any git checkouts within the specified scope are dirty (have local
 * modifications) and logs their state.
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
        name = "is-dirty", threadSafe = true)
public class IsDirtyMojo extends ScopedCheckoutsMojo
{

    public IsDirtyMojo()
    {
        super(true);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project, GitCheckout myCheckout,
            ProjectTree tree, List<GitCheckout> checkouts) throws Exception
    {
        var dirty = false;
        for (var checkout : checkouts)
        {
            if (checkout.isDirty())
            {
                if (!dirty)
                {
                    System.out.println("Dirty projects:");
                }
                dirty = true;
                System.out.println("* " + checkout);
            }
        }
        if (!dirty)
        {
            log.info("\nClean\n\n");
        }
    }
}