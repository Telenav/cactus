package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.trigger.RunPolicies;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Simply prints a highly visible message to the console.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "print-message", threadSafe = true)
public class PrintMessageMojo extends BaseMojo
{

    @Parameter(property = "cactus.message", required = true)
    private String message;

    public PrintMessageMojo()
    {
        super(RunPolicies.LAST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (message != null)
        {
            System.out.println(
                    "\n*********************************************************\n\n");
            System.out.println(message);
            System.out.println(
                    "\n\n*********************************************************\n");
        }
    }

}
