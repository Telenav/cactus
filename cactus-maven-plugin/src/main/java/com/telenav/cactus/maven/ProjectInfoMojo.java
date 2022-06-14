package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.util.PathUtils;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static java.lang.Math.max;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * A mojo that simply pretty-prints what a build is going to build.
 *
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.VERIFY,
                                           requiresDependencyResolution = ResolutionScope.NONE,
                                           instantiationStrategy = SINGLETON,
                                           name = "project-info", threadSafe = true)
public class ProjectInfoMojo extends BaseMojo
{
    @Override
    protected void performTasks(BuildLog log, MavenProject project)
    {
        System.out.println(generateInfo(project));
    }

    private CharSequence generateInfo(MavenProject project)
    {
        return "┋ Building " + project.getName();
    }
}
