package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.util.PathUtils;
import com.telenav.lexakai.Lexakai;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Runs lexakai to generate documentation and diagrams for a project into some
 * folder. This mojo is intended to be used only on the root of a family of
 * projects.
 * <p>
 * The destination folder for documentation is computed as follows:
 * </p>
 * <ol>
 * <li>If the <code>output-folder</code> parameter is passed with
 * <code>-Doutput-folder=path/to/output</code> or set in the
 * <code>&lt;configuration&rt;</code>, section of the invoking
 * <code>pom.xml</code> or its parents, that path will be used unmodified.</li>
 * <li>If an assets-home environment variable is set, used that. The environment
 * variable is computed as follows:
 * <ol>
 * <li>Take the suffix of the project's group-id</li>
 * <li>If it contains a hyphen, trim it to the text preceding the first
 * hyphen</li>
 * <li>Convert the string to upper case</li>
 * <li>Append <code>_ASSETS_HOME</code> to that</li>
 * </ol>
 * So, if you have a group id <code>com.telenav.kivakit</code>, the environment
 * variable is <code>KIVAKIT_ASSETS_HOME</code>; if you have a group id
 * <code>edu.stuff.foo-bar-baz</code> the environment variable is
 * <code>FOO_ASSETS_HOME</code>.
 * </li>
 * <li>If the environment variable is unset, then
 * <ul>
 * <li>Use steps 1 and 2 above to compute the project family name, and then</li>
 * <li>Find the git submodule root checkout above the project's base dir</li>
 * <li>Look for a folder named <code>$PROJECT_FAMILY-assets</code> and use it if
 * it exists</li>
 * </ul>
 * <li>If all of the above fail, output to <code>target/lexakai</code></li>
 * </ol>
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

    /**
     * If true, instruct lexakai to overwrite resources.
     */
    @Parameter(property = "overwrite-resources", defaultValue = "true", name = "overwrite-resources")
    private boolean overwriteResources;

    /**
     * If true, instruct lexakai to update readme files.
     */
    @Parameter(property = "update-readme", defaultValue = "true", name = "update-readme")
    private boolean updateReadme;

    /**
     * If true, log the commands being passed to lexakai.
     */
    @Parameter(property = "verbose", defaultValue = "true")
    private boolean verbose;

    /**
     * If true, don't really run lexakai.
     */
    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    /**
     * The destination folder for generated documentation - if unset, it is
     * computed as described above.
     */
    @Parameter(property = "output-folder", name = "output-folder")
    private String outputFolder;

    @Override
    protected boolean isOncePerSession()
    {
        return true;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        List<String> args = Arrays.asList(
                "-update-readme=" + updateReadme,
                "-overwrite-resources=" + overwriteResources,
                "-output-folder=" + output(project),
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

    Path output(MavenProject project)
    {
        // If the output folder was explicitly specified, use it.
        if (outputFolder != null)
        {
            return Paths.get(outputFolder);
        }
        // If the environment variable is set, use it
        String envValue = System.getenv(environmentVariableName(project));
        if (envValue != null)
        {
            return Paths.get(envValue);
        }
        // Find the root of the checkout, and if there is a
        // groupIdAfterLastDot-assets folder, use that; if not,
        // invent a target/lexakai dir in target/ for output
        return GitCheckout.repository(project.getBasedir()).flatMap(repo ->
        {
            return repo.submoduleRoot().flatMap(subroot
                    -> PathUtils.ifDirectory(
                            subroot.checkoutRoot().resolve(prefix(project) + "-assets"))).toOptional();
        }).orElseGet(() -> project.getBasedir().toPath().resolve("target").resolve("lexakai"));
    }

    private String prefix(MavenProject project)
    {
        String gid = project.getGroupId();
        int ix = gid.lastIndexOf('.');
        if (ix >= 0 && ix < gid.length() - 1)
        {
            return gid.substring(ix + 1);
        }
        return gid;
    }

    private String environmentVariableName(MavenProject project)
    {
        String tail = prefix(project).toUpperCase();
        int hyphen = tail.indexOf('-');
        if (hyphen > 0)
        {
            tail = tail.substring(0, hyphen);
        }
        return tail + "_ASSETS_HOME";
    }
}
