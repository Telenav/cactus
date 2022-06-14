package com.telenav.cactus.maven;

import com.telenav.cactus.build.metadata.BuildMetadata;
import com.telenav.cactus.build.metadata.BuildMetadataUpdater;
import com.telenav.cactus.maven.git.GitCheckout;
import static com.telenav.cactus.maven.git.GitCheckout.repository;
import com.telenav.cactus.maven.log.BuildLog;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Generates build.properties and project.properties files into
 * <code>target/classes/project.properties</code> and
 * <code>target/classes/build.properties</code> (configurable using the
 * <code>project-properties-dest</code> property).
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "build-metadata", threadSafe = true)
public class BuildMetadataMojo extends BaseMojo
{

    /**
     * The relative path to the destination directory.
     */
    @Parameter(property = "project-properties-dest", defaultValue = "target/classes/project.properties")
    private String projectPropertiesDest;

    /**
     * If true, log the contents of generated files.
     */
    @Parameter(property = "verbose", defaultValue = "false")
    private boolean verbose;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if ("pom".equals(project.getPackaging()))
        {
            log.info("Not writing project metadata for a non-java project.");
            return;
        }
        Path propsFile = project.getBasedir().toPath().resolve(projectPropertiesDest);
        if (!Files.exists(propsFile.getParent()))
        {
            Files.createDirectories(propsFile.getParent());
        }
        String propertiesFileContent = projectProperties(project);
        Files.writeString(propsFile, propertiesFileContent,
                UTF_8, WRITE, TRUNCATE_EXISTING, CREATE);
        List<String> args = new ArrayList<>(8);
        args.add(propsFile.getParent().toString());
        Optional<GitCheckout> checkout = repository(project.getBasedir());
        if (!checkout.isPresent())
        {
            log.warn("Did not find a git checkout for " + project.getBasedir());
        }
        checkout.ifPresent(repo ->
        {
            args.add(BuildMetadata.KEY_GIT_COMMIT_HASH);
            args.add(repo.head());

            args.add(BuildMetadata.KEY_GIT_REPO_CLEAN);
            args.add(Boolean.toString(!repo.isDirty()));

            repo.commitDate().ifPresent(when ->
            {
                args.add(BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP);
                args.add(when.format(DateTimeFormatter.ISO_DATE_TIME));
            });
        });
        BuildMetadataUpdater.main(args.toArray(String[]::new));
        if (verbose)
        {
            log.info("Wrote project.properties");
            log.info("------------------------");
            log.info("to " + propsFile + "\n");
            log.info(propertiesFileContent + "\n");
            Path buildProps = propsFile.getParent().resolve("build.properties");
            if (Files.exists(buildProps))
            {
                log.info("Wrote build.properties");
                log.info("----------------------");
                log.info("to " + buildProps + "\n");
                log.info(Files.readString(buildProps));
            } else
            {
                log.warn("No build file was generated in " + buildProps);
            }
        }
    }

    private String projectProperties(MavenProject project)
    {
        StringBuilder sb = new StringBuilder();
        String name = project.getName();
        if (name == null)
        {
            name = project.getArtifactId();
        }
        return sb.append("project-name=").append(name)
                .append("\nproject-version=").append(project.getVersion())
                .append("\nproject-group-id=").append(project.getGroupId())
                .append("\nproject-artifact-id=").append(project.getArtifactId())
                .append('\n').toString();
    }
}
