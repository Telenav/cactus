package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.SITE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        instantiationStrategy = SINGLETON,
        name = "lexakai", threadSafe = true)
public class LexakaiMojo extends BaseMojo {
    
    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        throw new UnsupportedOperationException("Run lexakai here.");
    }
}
