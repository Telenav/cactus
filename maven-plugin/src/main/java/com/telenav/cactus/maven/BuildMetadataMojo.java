package com.telenav.cactus.maven;

import com.telenav.cactus.build.metadata.BuildMetadata;
import com.telenav.cactus.build.metadata.BuildMetadataUpdater;
import com.telenav.cactus.maven.git.GitCheckout;
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
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "build-metadata", threadSafe = true)
public class BuildMetadataMojo extends BaseMojo
{

    @Parameter(property = "project-properties-dest", defaultValue = "src/main/java/project.properties")
    private String projectPropertiesDest;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        Path propsFile = project.getBasedir().toPath().resolve(projectPropertiesDest);
        if (!Files.exists(propsFile.getParent()))
        {
            Files.createDirectories(propsFile.getParent());
        }
        Files.writeString(propsFile, projectProperties(project),
                UTF_8, WRITE, TRUNCATE_EXISTING, CREATE);
        List<String> args = new ArrayList<>(8);
        args.add(propsFile.getParent().toString());
        GitCheckout.repository(propsFile).ifPresent(repo -> {
            args.add(BuildMetadata.KEY_GIT_COMMIT_HASH);
            args.add(repo.head());
            
            args.add(BuildMetadata.KEY_GIT_REPO_CLEAN); 
            args.add(Boolean.toString(!repo.isDirty()));
            
            args.add(BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP);
            args.add(repo.commitDate().format(DateTimeFormatter.ISO_DATE_TIME));
        });
        BuildMetadataUpdater.main(args.toArray(String[]::new));
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
