package com.telenav.cactus.maven;

import com.mastfrog.function.throwing.ThrowingBiConsumer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.project.MavenProject;

/**
 * A base class for our mojos. Simply
 *
 * @author Tim Boudreau
 */
abstract class BaseMojo extends AbstractMojo
{

    // These are magically injected by Maven:
    @Component
    private MavenProject project;

    protected MavenProject project()
    {
        if (project == null)
        {
            throw new IllegalStateException("Project was not injected");
        }
        return project;
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        run(this::performTasks);
    }

    protected abstract void performTasks(BuildLog log, MavenProject project) throws Exception;

    private void run(ThrowingBiConsumer<BuildLog, MavenProject> run) throws MojoExecutionException, MojoFailureException
    {
        try
        {
            BuildLog.run(log -> run.accept(log, project()));
        } catch (MojoFailureException | MojoExecutionException e)
        {
            throw e;
        } catch (Exception | Error e)
        {
            throw new MojoFailureException(e);
        }
    }

}
