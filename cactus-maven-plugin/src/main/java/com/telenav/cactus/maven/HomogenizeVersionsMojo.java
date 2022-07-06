package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.mojobase.SharedProjectTreeMojo;
import com.telenav.cactus.maven.refactoring.PropertyHomogenizer;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.nio.file.Path;
import java.util.Set;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * A project tree with multiple families may develop a variety of divergent
 * version properties for the same thing. This mojo will simply find all such
 * properties (for property names that indicate a version of a project or family
 * in the tree), and sets them to the greatest value found.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "homogenize-versions", threadSafe = true)
public class HomogenizeVersionsMojo extends SharedProjectTreeMojo
{
    public HomogenizeVersionsMojo()
    {
        super(RunPolicies.FIRST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        withProjectTree(tree ->
        {
            PropertyHomogenizer ph = new PropertyHomogenizer(new Poms(tree
                    .allProjects()));
            if (isPretend())
            {
                ph.pretend();
            }
            Set<Path> updated = ph.go(log::info);
            if (updated.isEmpty())
            {
                log.info("No inconsistent properties found.");
            }
            else
            {
                log.info("Updated " + updated.size() + " pom files.");
            }
        });
    }
}
