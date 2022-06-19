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

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.streams.stdio.ThreadMappedStdIO;
import com.telenav.cactus.cli.PathUtils;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.scope.ProjectFamily;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicies;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Runs lexakai to generate documentation and diagrams for a project into some folder. This mojo is intended to be used
 * only on the root of a family of projects.
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
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->",
            Pattern.DOTALL | Pattern.MULTILINE);

    class LexakaiRunner implements ThrowingRunnable
    {

        private final Path jarFile;

        private final List<String> args;

        private final BuildLog runLog = BuildLog.get().child("lexakai-runner");

        LexakaiRunner(Path jarFile, List<String> args)
        {
            this.jarFile = jarFile;
            this.args = args;
        }

        @Override
        public void run() throws Exception
        {
            ClassLoader ldr = Thread.currentThread().getContextClassLoader();
            try
            {
                URL[] url = new URL[]
                        {
                                new URL("jar:" + jarFile.toUri().toURL() + "!/")
                        };
                runLog.warn("Invoke lexakai reflectivly from " + url[0]);
                try (URLClassLoader jarLoader = new URLClassLoader("lexakai",
                        url, ldr))
                {
                    Thread.currentThread().setContextClassLoader(jarLoader);
                    Class<?> what = jarLoader.loadClass(
                            "com.telenav.lexakai.Lexakai");
                    Method mth = what.getMethod("main", String[].class);
                    runLog.info("Invoking lexakai " + mth + " on " + what
                            .getName());
                    mth.invoke(null, (Object) args.toArray(String[]::new));
                    runLog.info("Lexakai done.");
                }
            }
            finally
            {
                Thread.currentThread().setContextClassLoader(ldr);
                Path dir = output(project());
                // If we're on a project that generated nothing (some poms),
                // don't leave behind an empty directory for it
                if (Files.exists(dir) && Files.list(dir).count() == 0)
                {
                    Files.delete(dir);
                }
                else
                {
                    minimizeSVG(dir);
                }
            }
        }
    }

    /**
     * If true, instruct lexakai to overwrite resources.
     */
    @Parameter(property = "cactus.overwrite-resources", defaultValue = "true")
    private boolean overwriteResources;

    /**
     * If true, instruct lexakai to update readme files.
     */
    @Parameter(property = "cactus.update-readme", defaultValue = "true")
    private boolean updateReadme;

    /**
     * If true, log the commands being passed to lexakai.
     */
    @Parameter(property = "cactus.verbose", defaultValue = "true")
    private boolean verbose;

    /**
     * If true, don't really run lexakai.
     */
    @Parameter(property = "cactus.lexakai-skip", defaultValue = "false")
    private boolean skip;

    /**
     * The destination folder for generated documentation - if unset, it is computed as described above.
     */
    @Parameter(property = "cactus.output-folder")
    private String outputFolder;

    /**
     * The destination folder for generated documentation - if unset, it is computed as described above.
     */
    @Parameter(property = "cactus.commit-changes", defaultValue = "false")
    private boolean commitChanges;

    /**
     * The destination folder for generated documentation - if unset, it is computed as described above.
     */
    @Parameter(property = "cactus.lexakai-version", defaultValue = "1.0.7")
    private String lexakaiVersion = "1.0.7";

    /**
     * By default, code is generated into directories that match the relative directory structure from the
     * project-family root; if true, the relative directories are omitted so all projects' documentation are immediately
     * below the output folder.
     */
    @Parameter(property = "cactus.flatten", defaultValue = "false")
    private boolean flatten;

    /**
     * By default we strip XML comments from generated SVG, to minimize spurious diffs; if true that functionality is
     * disabled.
     */
    @Parameter(property = "cactus.no-minimize", defaultValue = "false")
    private boolean noMinimize;

    /**
     * Lexakai pribts vast and voluminous output which we suppress by default.
     */
    @Parameter(property = "cactus.show-lexakai-output", defaultValue = "false")
    private boolean showLexakaiOutput;

    /**
     * The repository to download lexakai from (central by default).
     */
    @Parameter(property = "cactus.lexakai-repository",
               defaultValue = MAVEN_CENTRAL_REPO)
    private String lexakaiRepository = MAVEN_CENTRAL_REPO;

    public LexakaiMojo()
    {
        super(RunPolicies.POM_PROJECT_ONLY.and(RunPolicies.LAST));
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        Path outputDir = output(project);
        List<String> args = Arrays.asList(
                "-update-readme=" + updateReadme,
                "-overwrite-resources=" + overwriteResources,
                "-output-folder=" + outputDir,
                project.getBasedir().toString()
        );
        if (verbose)
        {
            log.info("Lexakai args:");
            log.info("lexakai " + args);
        }
        if (!skip)
        {
            runLexakai(args, project, log);
        }
    }

    Path output(MavenProject project)
    {
        return GitCheckout.repository(project.getBasedir())
                .map(co -> outputFolder(project, co))
                .orElseGet(
                        () -> project.getBasedir().toPath().resolve("target")
                                .resolve("lexakai"));
    }

    Path outputFolder(MavenProject prj, GitCheckout checkout)
    {
        // If the output folder was explicitly specified, use it.
        if (outputFolder != null)
        {
            appendProjectLexakaiDocPath(Paths.get(outputFolder), prj,
                    checkout);
        }
        // Uses env upCase($FAMILY)_ASSETS_PATH or looks for a
        // $name-assets folder in the submodule root
        return ProjectFamily.of(prj).assetsPath(checkout).map(assetsPath
                -> appendProjectLexakaiDocPath(assetsPath, prj, checkout)
        ).orElseGet(()
                -> appendProjectLexakaiDocPath(prj.getBasedir().toPath()
                .resolve("target").resolve(
                        "lexakai"), prj, checkout));
    }

    private Path appendProjectLexakaiDocPath(Path path, MavenProject prj,
                                             GitCheckout checkout)
    {
        if (checkout.name().isEmpty())
        {
            throw new IllegalArgumentException(
                    "Cannot use the root project " + checkout
                            + " for a lexakai path for " + prj);
        }

        Path result = path.resolve("docs")
                .resolve(prj.getVersion())
                .resolve("lexakai")
                .resolve(checkout.name());

        if (!flatten)
        {
            Path relPath = checkout.submoduleRelativePath().get();
            for (int i = 0; i < relPath.getNameCount() - 1; i++)
            {
                result = result.resolve(relPath.getName(i));
            }
        }
        return result;
    }

    private Set<GitCheckout> collectModifiedCheckouts(ProjectTree tree)
    {
        tree.invalidateCache();
        Set<GitCheckout> needingCommit = new HashSet<>();
        if (tree.isDirty(tree.root()))
        {
            needingCommit.add(tree.root());
        }
        for (GitCheckout gc : tree.nonMavenCheckouts())
        {
            if (gc.isDirty())
            {
                needingCommit.add(gc);
            }
        }
        for (GitCheckout gc : tree.allCheckouts())
        {
            if (gc.isDirty())
            {
                needingCommit.add(gc);
            }
        }
        return needingCommit;
    }

    private Set<GitCheckout> collectedChangedRepos(MavenProject prj,
                                                   ThrowingRunnable toRun)
    {
        return ProjectTree.from(prj).map(tree ->
        {
            Set<GitCheckout> needingCommitBefore = collectModifiedCheckouts(tree);
            toRun.run();
            Set<GitCheckout> needingCommitAfter = collectModifiedCheckouts(tree);
            needingCommitAfter.removeAll(needingCommitBefore);
            return needingCommitAfter;
        }).orElseGet(() ->
        {
            toRun.run();
            return Collections.emptySet();
        });
    }

    private String commitMessage(MavenProject prj, Set<GitCheckout> checkouts)
    {
        StringBuilder sb = new StringBuilder("Generated commit ")
                .append(prj.getGroupId())
                .append(":")
                .append(prj.getArtifactId())
                .append(":")
                .append(prj.getVersion())
                .append("\n\n");

        String user = System.getProperty("user.name");
        Path home = PathUtils.home();
        String host = System.getenv("HOST");
        sb.append("User:\t").append(user);
        sb.append("\nHome:\t").append(home);
        sb.append("\nHost:\t").append(host);
        sb.append("\nWhen:\t").append(Instant.now());
        sb.append("\n\n").append("Modified checkouts:\n");
        for (GitCheckout ch : checkouts)
        {
            sb.append("\n  * ").append(ch.name())
                    .append(" (").append(ch.checkoutRoot()).append(")");
        }
        return sb.append("\n").toString();
    }

    private Path lexakaiJar() throws MojoFailureException
    {
        return downloadArtifact("com.telenav.lexakai", "Lexakai", lexakaiVersion)
                .get();
    }

    private ThrowingRunnable lexakaiRunner(List<String> args) throws Exception
    {
        ThrowingRunnable result = new LexakaiRunner(lexakaiJar(), args);
        if (!showLexakaiOutput)
        {
            return () -> ThreadMappedStdIO.blackhole(result);
        }
        return result;
    }

    private void minimizeSVG(Path dirOrFile) throws IOException
    {
        if (Files.isDirectory(dirOrFile))
        {
            try (Stream<Path> str = Files.walk(dirOrFile, 512).filter(
                    pth -> !Files.isDirectory(pth) && pth.getFileName()
                            .toString().endsWith(".svg")))
            {
                str.forEach(path ->
                {
                    quietly(() -> minimizeSVG(path));
                });
            }
        }
        else
        {
            String text = new String(Files.readAllBytes(dirOrFile), UTF_8);
            String revised = XML_COMMENT.matcher(text).replaceAll("") + '\n';
            Files.write(dirOrFile, revised.getBytes(UTF_8), WRITE,
                    TRUNCATE_EXISTING);
        }
    }

    private void runLexakai(List<String> args, MavenProject project,
                            BuildLog log1) throws Exception
    {
        ThrowingRunnable runner = lexakaiRunner(args);
        if (commitChanges)
        {
            // Returns the set of repositories which were _not_ modified
            // *before* we ran lexakai, but are now
            Set<GitCheckout> modified = collectedChangedRepos(project,
                    runner);
            if (!modified.isEmpty())
            {
                // Commit each repo in deepest-child down order
                String msg = commitMessage(project, modified);
                for (GitCheckout ch : GitCheckout.depthFirstSort(modified))
                {
                    if (!ch.addAll())
                    {
                        log1.error("Add all failed in " + ch);
                        continue;
                    }
                    if (!ch.commit(msg))
                    {
                        log1.error("Commit failed in " + ch);
                    }
                }
                // Committing child repos may have generated changes in the
                // set of commits the submodule root points to, so make sure
                // we generate a final commit here so it points to our updates
                GitCheckout.repository(project.getBasedir())
                        .flatMap(prjCheckout -> prjCheckout.submoduleRoot()
                                .toOptional())
                        .ifPresent(root ->
                        {
                            if (root.isDirty())
                            {
                                if (!root.addAll())
                                {
                                    log1.error("Add all failed in " + root);
                                }
                                if (!root.commit(msg))
                                {
                                    log1.error("Commit failed in " + root);
                                }
                            }
                        });
            }
        }
        else
        {
            runner.run();
        }
    }
}
