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

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.cli.PathUtils;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.scope.ProjectFamily;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.cli.PathUtils.copyFolderTree;

/**
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.POST_SITE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        name = "copy-javadoc", threadSafe = true)
public class CopyJavadocMojo extends BaseMojo
{

    private static final String PROP_DO_NOT_DEPLOY = "do.not.publish";
    private static final String PROP_JAVADOC_SKIP = "maven.javadoc.skip";

    /**
     * The relative path from the project basedir to the location of javadoc
     * output.
     */
    @Parameter(property = "javadoc-relative-path",
            defaultValue = "target/site/apidocs")
    private String javadocRelativePath = "target/site/apidocs";

    /**
     * If set, delete any files in the destination before copying.
     */
    @Parameter(property = "delete-existing",
            defaultValue = "false")
    private boolean deleteExisting;

    /**
     * If set, do not actually perform filesystem operations; just log what
     * would be done.
     */
    @Parameter(property = "telenav.pretend",
            defaultValue = "false")
    private boolean pretend;

    /**
     * The Javadoc mojo's skip property, so we can test if there will be no
     * javadoc to copy. If set to true, the project will be skipped.
     */
    @Parameter(property = "javadoc.skip",
            defaultValue = "false")
    private boolean javadocSkip;

    /**
     * The telenav do-not-deploy-to-maven-central property, used by projects
     * which are unit tests only and have no use as a dependency. If set to
     * true, the project will be skipped.
     */
    @Parameter(property = "do.not.deploy",
            defaultValue = "false")
    private boolean doNotDeploy;

    public CopyJavadocMojo(RunPolicy policy)
    {
        super(policy);
    }

    public CopyJavadocMojo()
    {
    }

    @Override
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        Path path = Paths.get(javadocRelativePath);
        if (path.isAbsolute())
        {
            fail("Javadoc relative path cannot be an absolute path: " + path);
        }
    }

    private Path deriveJavadocDestination(Path assetsPath, MavenProject project,
            GitCheckout checkout)
    {
        if (checkout.name().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Attempt to copy javadoc for the root project.");
        }
        if ("pom".equals(project.getPackaging()))
        {
            return assetsPath.resolve("docs")
                    .resolve(project.getVersion())
                    .resolve("javadoc")
                    .resolve(checkout.name());
        }

        return assetsPath.resolve("docs")
                .resolve(project.getVersion())
                .resolve("javadoc")
                .resolve(checkout.name())
                .resolve(project.getArtifactId());
    }

    private boolean isSkipped(MavenProject project)
    {
        if (javadocSkip || doNotDeploy)
        {
            return true;
        }
        Properties props = project.getProperties();
        String propValue = props.getProperty(PROP_DO_NOT_DEPLOY,
                props.getProperty(PROP_JAVADOC_SKIP, "false")).trim();

        // Ant conventions
        boolean result = "true".equals(propValue) || "yes".equals(propValue)
                || "on".equals(propValue);
        if (!result)
        {
            // Do not try to copy javadoc for the root project, only child
            // families
            result = GitCheckout.repository(project.getBasedir())
                    .map(GitCheckout::isSubmoduleRoot).orElse(false);
        }
        return result;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (isSkipped(project))
        {
            log.info(
                    "do.not.deploy or javadoc.skip set on " + project + " - skipping.");
            return;
        }
        Path javadocPath = project.getBasedir().toPath().resolve(
                javadocRelativePath);

        // If we're in a javadoc project, we are only copying the module 
        // subdirectories; in a pom project, we want to copy the whole structure
        boolean isPom = "pom".equals(project.getPackaging());
        if (!isPom)
        {
            javadocPath = javadocPath.resolve("src");
        }
        boolean javadocExists = Files.exists(javadocPath);
        if (isPom && !javadocExists)
        {
            if (!RunPolicies.LAST.shouldRun(project, session()))
            {
                log.info(
                        "No javadoc, but " + project.getArtifactId()
                        + " is an intermediate pom project. Skipping");
                return;
            }
        }

        if (!javadocExists)
        {
            fail("No javadoc found at " + javadocPath
                    + " javadoc must be built before this mojo is run."
                    + " PROPERTIES:\n" + project.getProperties());
        }
        copyJavadoc(javadocPath, project, log);
    }

    private void copyJavadoc(Path javadocOrigin, MavenProject project,
            BuildLog log)
            throws Exception
    {
        ProjectFamily family = ProjectFamily.of(project);
        ThrowingOptional.from(GitCheckout
                .repository(project.getBasedir()))
                .flatMapThrowing(checkout
                        -> family.assetsPath(checkout).map(assetsPath
                        -> deriveJavadocDestination(assetsPath, project,
                        checkout)
                )).ifPresentOrElse(dest ->
        {
            if (dest.equals(javadocOrigin) || dest.startsWith(javadocOrigin)
                    || javadocOrigin.startsWith(dest))
            {
                fail("Will not copy javadoc recursively into itself: "
                        + javadocOrigin + " vs " + dest);
            }
            if (deleteExisting && !pretend)
            {
                int deleted = PathUtils.deleteFolderTree(dest);
                if (deleted > 0)
                {
                    log.info("Deleted " + deleted + " files in " + dest);
                }
            }

            log.warn("Copying javadoc for " + project + " in family " + family
                    + " into " + dest + " from " + javadocOrigin);
            if (!pretend)
            {
                int[] filesAndDirs = copyFolderTree(log, javadocOrigin,
                        dest);
                log.info(
                        "Copied " + filesAndDirs[0] + " files and created "
                        + filesAndDirs[1] + " folders under " + dest);
            }
        }, () -> log.warn(
                "Could not find git checkout or assets path to move javadoc for " + project)
        );
    }
}
