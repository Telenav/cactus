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
package com.telenav.cactus.maven.mojobase;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.cactus.preferences.CactusPreferences;
import com.telenav.cactus.cactus.preferences.Preference.StringPreference;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.ArtifactFinder;
import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.shared.SharedDataKey;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.io.IOException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.cactus.preferences.CactusPreferences.cactusPreferences;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PRETEND;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.VERBOSE;
import static java.awt.Desktop.getDesktop;
import static java.awt.Desktop.isDesktopSupported;

/**
 * A base class for our mojos, which sets up a build logger and provides a way
 * to access some commonly needed types.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "UnusedReturnValue"
        })
public abstract class BaseMojo extends AbstractMojo
{
    protected static final String MAVEN_CENTRAL_REPO
            = "https://repo1.maven.org/maven2";

    private static final SharedDataKey<AutomergeTag> AUTOMERGE_TAG_KEY
            = SharedDataKey.of(AutomergeTag.class);

    private final ThreadLocal<Boolean> running = ThreadLocal.withInitial(
            () -> false);
    private final SharedDataKey<AtomicBoolean> thisMojoWasRunKey
            = SharedDataKey.of(
                    getClass().getName(), AtomicBoolean.class);

    /**
     * Allows type-safe key value pairs to be shared between mojos.
     */
    @Inject
    SharedData sharedData;

    public final SharedData sharedData()
    {
        return sharedData;
    }

    boolean isRunning()
    {
        return running.get();
    }

    protected AutomergeTag automergeTag()
    {
        return sharedData().computeIfAbsent(AUTOMERGE_TAG_KEY,
                () -> new AutomergeTag(session()));
    }

    public final boolean isFirstRunInThisSession()
    {
        if (isRunning())
        {
            Optional<AtomicBoolean> opt = sharedData().get(thisMojoWasRunKey);
            return !opt.isPresent() || !opt.get().get();
        }
        return false;
    }

    public final boolean wasRunInThisSession()
    {
        Optional<AtomicBoolean> opt = sharedData().get(thisMojoWasRunKey);
        return opt.isPresent() && opt.get().get();
    }

    /**
     * Run some code which throws an exception in a context such as
     * <code>Stream.forEach()</code> where you cannot throw checked exceptions.
     *
     * @param code Something to run
     */
    protected static void quietly(ThrowingRunnable code)
    {
        code.toNonThrowing().run();
    }

    /**
     * Run some code which throws an exception in a context such as
     * <code>Stream.forEach()</code> where you cannot throw checked exceptions.
     *
     * @param code Something to run
     */
    protected static <T> T quietly(ThrowingSupplier<T> code)
    {
        return code.asSupplier().get();
    }

    protected static final class ArtifactFetcher
    {
        private String extension = "jar";

        private String repositoryUrl = MAVEN_CENTRAL_REPO;

        private final String groupId;

        private final String artifactId;

        private final String version;

        private String classifier;

        private final BuildLog log;

        private final MavenSession session;

        private ArtifactFetcher(String groupId, String artifactId,
                String version, MavenSession session)
        {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
            this.log = BuildLog.get().child("fetch").child(groupId).child(
                    artifactId).child(version);
            this.session = session;
        }

        /**
         * Download the artifact if needed, returning a Path to it in the local
         * repository.
         *
         * @return A path
         */
        public Path get() throws MojoFailureException
        {
            Artifact artifact = new DefaultArtifact(
                    notNull("groupId", groupId),
                    notNull("artifactId", artifactId),
                    classifier,
                    notNull("type", extension),
                    notNull("version", version));

            LocalArtifactRequest request = new LocalArtifactRequest();
            request.setArtifact(artifact);
            RemoteRepository remoteRepo = new RemoteRepository.Builder("central",
                    "x", repositoryUrl).build();

            request.setRepositories(Collections.singletonList(remoteRepo));
            RepositorySystemSession session = this.session
                    .getRepositorySession();
            LocalArtifactResult result = session.getLocalRepositoryManager()
                    .find(session, request);

            log.info("Download result for " + artifact + ": " + result);
            if (result != null && result.getFile() != null)
            {
                log.info("Have local " + artifactId + " " + extension + " "
                        + result.getFile());
                return result.getFile().toPath();
            }
            throw new MojoFailureException(
                    "Could not download " + artifact + " from "
                    + remoteRepo.getUrl());
        }

        /**
         * Change the repository URL used (the default is Maven Central).
         *
         * @param repositoryUrl A repository URL
         * @return this
         */
        @SuppressWarnings("ResultOfObjectAllocationIgnored")
        public ArtifactFetcher withRepositoryURL(String repositoryUrl)
        {
            try
            {
                new URL(notNull("repoUrl", repositoryUrl));
            }
            catch (MalformedURLException ex)
            {
                log.error("Invalid repository URL '" + repositoryUrl);
                return Exceptions.chuck(new MojoExecutionException(
                        "Invalid repository URL '" + repositoryUrl + '\''));
            }
            this.repositoryUrl = repositoryUrl;
            return this;
        }

        /**
         * Set the artifact type, if you want something other than the default
         * of "jar".
         *
         * @param extension A type
         * @return this
         */
        public ArtifactFetcher withExtension(String extension)
        {
            this.extension = notNull("type", extension);
            return this;
        }

        public ArtifactFetcher withClassifier(String classifier)
        {
            this.classifier = notNull("type", classifier);
            return this;
        }

    }

    // These are magically injected by Maven:
    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true)
    private volatile MavenSession mavenSession;

    @Parameter(property = VERBOSE, defaultValue = "false", alias = "verbose")
    private boolean verbose;

    /**
     * If true, do not actually make changes, just print what would be done.
     */
    @Parameter(property = PRETEND, defaultValue = "false", alias = "pretend")
    private boolean pretend;

    protected BuildLog log;

    ThrowingOptional<ProjectTree> tree;

    private final RunPolicy policy;

    protected BaseMojo(RunPolicy policy)
    {
        this.policy = notNull("policy", policy);
    }

    protected BaseMojo()
    {
        this(RunPolicies.EVERY);
    }

    protected BaseMojo(boolean oncePerSession)
    {
        this(oncePerSession
             ? RunPolicies.LAST_CONTAINING_GOAL
             : RunPolicies.FIRST);
    }

    /**
     * Generic "don't really do anything" parameter - if this returns true, the
     * subclass should not really make changes, but log what it would do as
     * accurately as possible.
     *
     * @return True if we are in pretend mode
     */
    protected boolean isPretend()
    {
        return pretend;
    }

    /**
     * Run some code only if not in pretend-mode.
     *
     * @param code The code to run
     * @throws Exception if something goes wrong
     */
    protected void ifNotPretending(ThrowingRunnable code)
    {
        if (!pretend)
        {
            code.toNonThrowing().run();
        }
    }

    public final String goal()
    {
        // Pending: Create an annotation processor based way so we don't
        // need to duplicate the goal with a runtime annotation.
        BaseMojoGoal bmg = getClass().getAnnotation(BaseMojoGoal.class);
        if (bmg != null)
        {
            return bmg.value();
        }
        log().error("Could not find a goal name in annotations on "
                + getClass().getName());
        return "";
    }

    /**
     * Implementation of <code>Mojo.execute()</code>, which delegates to
     * <code>performTasks()</code> after validating the parameters.
     *
     * @throws MojoExecutionException If mojo execution fails
     * @throws MojoFailureException If the mojo could not be executed
     */
    @Override
    public final void execute() throws MojoExecutionException, MojoFailureException
    {
        AtomicBoolean run = sharedData().computeIfAbsent(thisMojoWasRunKey,
                AtomicBoolean::new);
        boolean old = running.get();
        try
        {
            running.set(true);
            if (policy.shouldRun(this, project))
            {
                run.set(true);
                run(this::performTasks);
            }
            else
            {
                new BuildLog(getClass()).info("Skipping " + getClass()
                        .getSimpleName() + " mojo per policy " + policy);
            }
        }
        finally
        {
            // Allow for reentrancy, just in case
            running.set(old);
        }
    }

    /**
     * Simplified way to throw a MojoExecutionException.
     *
     * @param <T> A type
     * @param message A message
     * @return Nothing, but parameterized so that this method can be an exit
     * point of any method that returns something
     * @throws MojoExecutionException always, using the passed message
     */
    public <T> T fail(Object message)
    {
        String s = Objects.toString(message);
        return Exceptions.chuck(new MojoExecutionException(this, s,
                s));
    }

    protected Runnable failingWith(String msg)
    {
        return () -> fail(msg);
    }

    /**
     * Downloads or finds in the local repo an artifact from maven central
     * (overridable) independent of what the dependencies of the project are.
     *
     * @param groupId A group id
     * @param artifactId An artifact id
     * @param version A version
     * @return An ArtifactFetcher which can be used to configure the artifact
     * type and repository if needed, and then fetch the artifact.
     */
    @SuppressWarnings("SameParameterValue")
    protected ArtifactFetcher downloadArtifact(String groupId, String artifactId,
            String version)
    {
        return new ArtifactFetcher(groupId, artifactId, version, mavenSession);
    }

    @SuppressWarnings("SameParameterValue")
    protected ArtifactFetcher downloadArtifact(String groupId, String artifactId,
            String version, String classifier)
    {
        return new ArtifactFetcher(groupId, artifactId, version, mavenSession)
                .withClassifier(classifier);
    }

    /**
     * Get the build log for this mojo.
     *
     * @return a logger
     */
    protected final BuildLog log()
    {
        if (log == null)
        {
            log = new BuildLog(getClass());
        }
        return log;
    }

    /**
     * Override to do the work of this mojo.
     *
     * @param log A log
     * @param project The project
     * @throws Exception If something goes wrong
     */
    protected abstract void performTasks(BuildLog log, MavenProject project)
            throws Exception;

    /**
     * Get the project this mojo is invoked against.
     *
     * @return A project
     */
    public final MavenProject project()
    {
        return project;
    }

    protected final ThrowingOptional<ProjectTree> projectTree(
            boolean invalidateCache)
    {
        return projectTreeInternal(invalidateCache);
    }

    /**
     * Get a project tree for the project this mojo is run on. Note this is an
     * expensive operation.
     *
     * @return An optional
     */
    protected final ThrowingOptional<ProjectTree> projectTree()
    {
        return projectTree(true);
    }

    /**
     * Override to return true if the mojo is intended to run exactly one time
     * for *all* repositories in the checkout, and should not do its work once
     * for every sub-project when called from a multi-module pom.
     *
     * @return true if the mojo should only be run once, on the last project
     */
    protected RunPolicy runPolicy()
    {
        return policy;
    }

    /**
     * Get the maven session associated with this mojo.
     *
     * @return A session
     */
    public final MavenSession session()
    {
        return mavenSession;
    }

    /**
     * Throws an exception if a branch name passed in is invalid.
     *
     * @param branchName A branch name
     * @param nullOk IF true and the branch is null, simply returns
     * @throws MojoExecutionException if the branch is invalid by these criteria
     */
    protected void validateBranchName(String branchName, boolean nullOk)
    {
        if (branchName == null)
        {
            if (nullOk)
            {
                return;
            }
            fail("Branch name unset");
        }
        else
            if (branchName.isBlank()
                    || branchName.contains(":")
                    || branchName.startsWith("-")
                    || branchName.contains(" ")
                    || branchName.contains("\"")
                    || branchName.contains("'"))
            {
                fail("Illegal branch name format: '" + branchName + "'");
            }
    }

    protected final boolean isVerbose()
    {
        return verbose;
    }

    protected final void ifVerbose(ThrowingRunnable run) throws Exception
    {
        if (isVerbose())
        {
            run.run();
        }
    }

    /**
     * Perform any fail-fast validation here; a super call is not needed.
     *
     * @param log The log
     * @param project A project
     * @throws Exception if something goes wrong
     */
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        // do nothing - for subclassers
    }

    /**
     * Run something against the project tree if one can be constructed.
     *
     * @param <T> The return value type
     * @param func A function applied to the project tree
     * @return An optional result
     */
    protected final <T> ThrowingOptional<T> withProjectTree(
            ThrowingFunction<ProjectTree, T> func)
    {
        return projectTree().map(func);
    }

    /**
     * Run something against the project tree.
     *
     * @param cons A consumer
     * @return true if the code was run
     */
    protected final boolean withProjectTree(ThrowingConsumer<ProjectTree> cons)
    {
        return projectTree().ifPresent(cons);
    }

    /**
     * Run something against the project tree if one can be constructed.
     *
     * @param <T> The return value type
     * @param invalidateCache Whether or not the tree's cache should be cleared
     * before returning the instance if it already existed
     * @param func A function applied to the project tree
     * @return An optional result
     */
    protected final <T> ThrowingOptional<T> withProjectTree(
            boolean invalidateCache,
            ThrowingFunction<ProjectTree, T> func)
    {
        return projectTree(invalidateCache).map(func);
    }

    /**
     * Run something against the project tree.
     *
     * @param invalidateCache Whether or not the tree's cache should be cleared
     * before returning the instance if it already existed
     * @param cons A consumer
     * @return true if the code was run
     */
    protected final boolean withProjectTree(boolean invalidateCache,
            ThrowingConsumer<ProjectTree> cons)
    {
        return projectTree(invalidateCache).ifPresent(cons);
    }

    void internalSubclassValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {

    }

    /**
     * Create the project try; package private so that SharedProjectTreeMojo can
     * use the shared data to cache the instance.
     *
     * @param invalidateCache Whether or not to invalidate the cache.
     * @return A project tree, if one can be constructed.
     */
    ThrowingOptional<ProjectTree> projectTreeInternal(boolean invalidateCache)
    {
        if (tree == null)
        {
            tree = ProjectTree.from(project());
        }
        else
        {
            if (invalidateCache)
            {
                tree.ifPresent(ProjectTree::invalidateCache);
            }
        }
        return tree;
    }

    private void internalValidateParameters(BuildLog log, MavenProject project)
            throws Exception
    {
        if (project == null)
        {
            throw new MojoFailureException("Project was not injected");
        }
        if (mavenSession == null)
        {
            throw new MojoFailureException("MavenSession was not injected");
        }
        if (sharedData == null)
        {
            fail("SharedData was not injected");
        }
        internalSubclassValidateParameters(log, project);
        validateParameters(log, project);
    }

    private void run(ThrowingBiConsumer<BuildLog, MavenProject> run)
            throws MojoExecutionException, MojoFailureException
    {
        try
        {
            BuildLog theLog = log();
            theLog.run(() ->
            {
                internalValidateParameters(theLog, project());
                run.accept(theLog, project());
            });
        }
        catch (MojoFailureException | MojoExecutionException e)
        {
            throw e;
        }
        catch (Exception | Error e)
        {
            Throwable t = e;
            if (e instanceof java.util.concurrent.CompletionException && e
                    .getCause() != null)
            {
                t = e.getCause();
            }
            if (e instanceof java.util.concurrent.ExecutionException && e
                    .getCause() != null)
            {
                t = e.getCause();
            }
            throw new MojoFailureException(t);
        }
    }

    /**
     * Open a URL on the user's desktop using the Java desktop API. Logs a
     * message in headless mode.
     *
     * @param uri
     * @param log
     */
    protected boolean open(String uri)
    {
        try
        {
            URI u = new URI(uri);
            open(u);
            return true;
        }
        catch (URISyntaxException ex)
        {
            log().error("Invalid uri " + uri, ex);
            return false;
        }
    }

    /**
     * Open a URL on the user's desktop using the Java desktop API. Logs a
     * message in headless mode.
     *
     * @param uri
     * @param log
     */
    protected void open(URI uri)
    {
        BuildLog log = log();
        // Get out of the way of the rest of maven
        // execution - initializing hunks of AWT is not free.
        if (isDesktopSupported())
        {
            log.info("Opening browser for " + uri);
            try
            {
                getDesktop().browse(uri);
            }
            catch (IOException ex)
            {
                log.error("Exception thrown opening " + uri, ex);
            }
        }
        else
        {
            log.error(
                    "Desktop not supported in this JVM; cannot open " + uri);
        }
    }

    protected void usingArtifactFinder(ThrowingRunnable run)
    {
        new ArtifactFinderImpl().run(run.toNonThrowing());
    }

    class ArtifactFinderImpl implements ArtifactFinder
    {
        @Override
        public Optional<Path> find(String groupId, String artifactId,
                String version, String type)
        {
            ArtifactFetcher fetcher = new ArtifactFetcher(groupId, artifactId,
                    version, mavenSession);
            if (type != null)
            {
                fetcher.withExtension(type);
            }
            try
            {
                Path path = fetcher.get();
                return Optional.ofNullable(path);
            }
            catch (Exception | Error ex)
            {
                log().error(
                        "Fetching " + groupId + ":" + artifactId + ":" + version,
                        ex);
            }
            return Optional.empty();
        }
    }

    protected final MavenCoordinates coordinatesOf(MavenProject project)
    {
        return new MavenCoordinates(notNull("project", project).getGroupId(),
                project.getArtifactId(), project.getVersion());
    }

    protected final Pom toPom(MavenProject project)
    {
        return Pom.from(project.getFile().toPath()).get();
    }

    /**
     * Used to print messages that
     *
     * @param message
     */
    protected void emitMessage(Object message)
    {
        if (message != null)
        {
            System.out.println(message);
        }
    }

    protected final CactusPreferences preferences()
    {
        MavenProject prj = project();
        Path dir = prj.getBasedir().toPath();
        return cactusPreferences(dir, prj::getProperties);
    }

    protected final CactusPreferences preferences(Path dir)
    {
        return cactusPreferences(dir);
    }

    protected final String property(Path in, String fieldValue,
            StringPreference pref)
    {
        return fieldValue == null
               ? preferences(in).get(pref)
               : fieldValue;
    }
}
