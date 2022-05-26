package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
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

    @Parameter(defaultValue = "${session}")
    private MavenSession mavenSession;

    protected BuildLog log;

    /**
     * Override to return true if the mojo is intended to run exactly one time
     * for *all* repositories in the checkout, and should not do its work once
     * for every sub-project when called from a multi-module pom.
     *
     * @return true if the mojo should only be run once, on the last project
     */
    protected boolean isOncePerSession()
    {
        return false;
    }

    protected BuildLog log()
    {
        if (log == null)
        {
            log = new BuildLog(getClass());
        }
        return log;
    }

    protected MavenProject project()
    {
        if (project == null)
        {
            throw new IllegalStateException("Project was not injected");
        }
        return project;
    }

    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        if (project == null)
        {
            throw new IllegalStateException("Project was not injected");
        }
    }

    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        if (isOncePerSession())
        {
            boolean isRoot = session().getExecutionRootDirectory().equalsIgnoreCase(project.getBasedir().toString());;
            if (!isRoot)
            {
                new BuildLog(getClass()).info("Skipping once-per-session mojo until the end.");
                return;
            }
        }
        run(this::performTasks);
    }

    protected final MavenSession session() throws MojoFailureException
    {
        if (mavenSession == null)
        {
            throw new MojoFailureException("Maven session not injected");
        }
        return mavenSession;
    }

    protected abstract void performTasks(BuildLog log, MavenProject project) throws Exception;

    private void run(ThrowingBiConsumer<BuildLog, MavenProject> run) throws MojoExecutionException, MojoFailureException
    {
        try
        {
            log().run(() ->
            {
                validateParameters(log(), project());
                run.accept(log(), project());
            });
        } catch (MojoFailureException | MojoExecutionException e)
        {
            throw e;
        } catch (Exception | Error e)
        {
            Throwable t = e;
            if (e instanceof java.util.concurrent.CompletionException && e.getCause() != null)
            {
                t = e.getCause();
            }
            throw new MojoFailureException(t);
        }
    }
}
