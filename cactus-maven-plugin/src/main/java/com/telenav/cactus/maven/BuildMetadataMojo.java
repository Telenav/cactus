////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.maven;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.git.GitCheckout.checkout;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_HASH;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_COMMIT_TIMESTAMP;
import static com.telenav.cactus.metadata.BuildMetadata.KEY_GIT_REPO_CLEAN;
import static com.telenav.cactus.metadata.BuildMetadataUpdater.main;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PROCESS_SOURCES;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Generates build.properties and project.properties files into
 * <code>target/classes/project.properties</code> and
 * <code>target/classes/build.properties</code> (configurable using the
 * <code>project-properties-dest</code> property).
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = PROCESS_SOURCES,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "build-metadata", threadSafe = true)
@BaseMojoGoal("build-metadata")
public class BuildMetadataMojo extends BaseMojo
{

    /**
     * The relative path to the destination directory.
     */
    @Parameter(property = "cactus.project-properties-destination",
            defaultValue = "target/classes/project.properties")
    private String projectPropertiesDestination;

    @Parameter(property = "cactus.build.metadata.skip")
    private boolean skip;
    
    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (skip) {
            log.info("Build metadata is skipped");
            return;
        }
        if ("pom".equals(project.getPackaging()))
        {
            log.info("Not writing project metadata for a non-java project.");
            return;
        }
        Path propsFile = project.getBasedir().toPath().resolve(
                projectPropertiesDestination);
        if (!exists(propsFile.getParent()))
        {
            createDirectories(propsFile.getParent());
        }
        String propertiesFileContent = projectProperties(project);
        writeString(propsFile, propertiesFileContent,
                UTF_8, WRITE, TRUNCATE_EXISTING, CREATE);
        List<String> args = new ArrayList<>(8);
        args.add(propsFile.getParent().toString());
        Optional<GitCheckout> checkout = checkout(project.getBasedir());
        if (checkout.isEmpty())
        {
            log.warn("Did not find a git checkout for " + project.getBasedir());
        }
        checkout.ifPresent(repo ->
        {
            args.add(KEY_GIT_COMMIT_HASH);
            args.add(repo.head());

            args.add(KEY_GIT_REPO_CLEAN);
            args.add(Boolean.toString(!repo.isDirty()));

            repo.commitDate().ifPresent(when ->
            {
                args.add(KEY_GIT_COMMIT_TIMESTAMP);
                args.add(when.format(ISO_DATE_TIME));
            });
        });
        main(args.toArray(String[]::new));
        ifVerbose(() ->
        {
            log.info("Wrote project.properties");
            log.info("------------------------");
            log.info("to " + propsFile + "\n");
            log.info(propertiesFileContent + "\n");
            Path buildProps = propsFile.getParent().resolve(
                    "build.properties");
            if (exists(buildProps))
            {
                log.info("Wrote build.properties");
                log.info("----------------------");
                log.info("to " + buildProps + "\n");
                log.info(readString(buildProps));
            }
            else
            {
                log.warn("No build file was generated in " + buildProps);
            }
        });
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
                .append("\nproject-artifact-id=")
                .append(project.getArtifactId())
                .append('\n').toString();
    }
}
