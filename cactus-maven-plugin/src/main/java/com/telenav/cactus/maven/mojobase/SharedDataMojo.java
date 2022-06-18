package com.telenav.cactus.maven.mojobase;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.trigger.RunPolicy;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

/**
 * Base class for mojos which use SharedData to share data either with other
 * mojos (like ForkBuildMojo and MergeForkBuildMojo) or between instances of
 * themselves without needing to be a singleton.
 *
 * @author Tim Boudreau
 */
public abstract class SharedDataMojo extends BaseMojo
{

    @Inject
    SharedData sharedData;

    public SharedDataMojo(RunPolicy policy)
    {
        super(policy);
    }

    public SharedDataMojo()
    {
    }

    public SharedDataMojo(boolean oncePerSession)
    {
        super(oncePerSession);
    }
    
    @Override
    void internalSubclassValidateParameters(BuildLog log, MavenProject project)
            throws MojoExecutionException
    {
        if (sharedData == null)
        {
            fail("SharedData was not injected");
        }
    }

    protected SharedData sharedData()
    {
        return sharedData;
    }
}
