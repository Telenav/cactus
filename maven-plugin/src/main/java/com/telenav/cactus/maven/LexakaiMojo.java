package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.lexakai.Lexakai;
import java.util.Arrays;
import java.util.List;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
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
public class LexakaiMojo extends BaseMojo
{

    @Parameter(property = "overwrite-resources", defaultValue = "true")
    private boolean overwriteResources;

    @Parameter(property = "update-readme", defaultValue = "true")
    private boolean updateReadme;

    @Parameter(property = "verbose", defaultValue = "true")
    private boolean verbose;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    @Parameter(property = "output-folder")
    private String outputFolder;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        String envVar = environmentVariableName(project);
        String envValue = System.getenv(envVar);
        log.info("Env var is " + envVar + " for " + project.getArtifactId() + " -> " + envValue);
        String out = outputFolder;
        if (out == null)
        {
            out = envValue;
        }
        if (out == null)
        {
            out = project.getBasedir().toPath().resolve("target").resolve("lexakai").toString();
        }

        List<String> args = Arrays.asList(
                "-project-version=" + project.getVersion(),
                "-update-readme=" + updateReadme,
                "-overwrite-resources=" + overwriteResources,
                "-output-folder=" + out,
                project.getBasedir().toString()
        );
        if (verbose)
        {
            log.info("Lexakai args:");
            log.info("lexakai " + args);
        }
        if (!pretend)
        {
            Lexakai.main(args.toArray(String[]::new));
        }
    }

    private String environmentVariableName(MavenProject project)
    {
        String gid = project.getGroupId();
        int ix = gid.lastIndexOf('.');
        String tail = gid.substring(ix + 1).toUpperCase();
        int hyphen = tail.indexOf('-');
        if (hyphen > 0)
        {
            tail = tail.substring(0, hyphen);
        }
        return tail + "_ASSETS_HOME";
    }
}
