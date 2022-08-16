package com.telenav.cactus.test.project;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.util.file.FileUtils;
import com.mastfrog.util.strings.LevenshteinDistance;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.trigger.RunPolicy;
import com.telenav.cactus.metadata.BuildMetadata;
import com.telenav.cactus.test.project.generator.GeneratedProjects;
import com.telenav.cactus.test.project.generator.MavenCommand;
import com.telenav.cactus.test.project.generator.ProjectsGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A tree of generated projects organized in families of projects within
 * multiple git submodules. A GeneratedProjectTree is a specific checked out
 * instance of a git repository with submodules, and can be cloned via
 * <code>newClone()</code> to create additional clones with different locations
 * on disk (all pushing to the same original), to simulate multiple users
 * working independently, producing merge conflicts and so forth.
 *
 * @author Tim Boudreau
 */
public abstract class GeneratedProjectTree<T extends GeneratedProjectTree<T>>
        implements ProjectWrapper
{
    static String cactusVersion;
    public static final Duration TIMEOUT = Duration.ofMinutes(2);
    private final Map<ProjectsGenerator.FakeProject, WrappedProjectImpl> wrapperForProject = new HashMap<>();
    private final Map<Pom, WrappedPomProject> wrapperForPomProject = new HashMap<>();
    protected final GeneratedProjects projects;
    protected final String groupIdBase;
    protected final String uid;
    private GitCheckout rootCheckout;
    private Poms poms;
    private Pom pom;

    public GeneratedProjectTree(GeneratedProjects projects, String groupIdBase,
            String uid)
    {
        this.projects = notNull("projects", projects);
        this.groupIdBase = notNull("groupIdBase", groupIdBase);
        this.uid = notNull("uid", uid);
    }

    @SuppressWarnings("unchecked")
    protected final T cast()
    {
        return (T) this;
    }

    @Override
    public void pomsChanged()
    {
        pom = null;
        poms = null;
        for (WrappedPomProject p : wrapperForPomProject.values()) {
            p.pomsChanged();
        }
        for (WrappedProjectImpl p : wrapperForProject.values()) {
            p.pomsChanged();
        }
    }

    /**
     * Find a project by artifact id.
     *
     * @param artifactId An artifact id
     * @return A project - throws if none
     */
    public final ProjectWrapper project(String artifactId)
    {
        try
        {
            ThrowingOptional<ProjectsGenerator.FakeProject> fpOpt = projects
                    .findProject(artifactId);
            if (fpOpt.isPresent())
            {
                ProjectsGenerator.FakeProject fp = fpOpt.get();
                return wrapperForProject.computeIfAbsent(fp, a ->
                {
                    return new WrappedProjectImpl(fp, this);
                });
            }
            Poms p = poms();
            ThrowingOptional<Pom> pomOpt = p.get(ArtifactId.of(artifactId));
            if (pomOpt.isPresent())
            {
                return wrapperForPomProject.computeIfAbsent(pomOpt.get(), pom ->
                {
                    return new WrappedPomProject(pom, this);
                });
            }
            throw new NoSuchElementException();
        }
        catch (NoSuchElementException e)
        {
            throw new IllegalArgumentException(
                    "No project with artifact id " + artifactId
                    + " in " + sortedMatches(artifactId), e);
        }
    }

    /**
     * Get a Poms instance representing all of the projects.
     *
     * @return A Poms
     */
    public final Poms poms()
    {
        if (poms == null)
        {
            try
            {
                poms = Poms.in(projects.cloneRoot());
            }
            catch (IOException ex)
            {
                throw new IllegalStateException("In " + projects.cloneRoot());
            }
        }
        return poms;
    }

    /**
     * Get a git checkout over the root supermodule project.
     *
     * @return A git checkout
     */
    public final GitCheckout getCheckout()
    {
        if (rootCheckout == null)
        {
            rootCheckout = GitCheckout.checkout(projects.cloneRoot()).get();
        }
        return rootCheckout;
    }

    /**
     * Get the base group id - group id's are uniquified so that tests are
     * unaffected by any local artifacts from previous test runs.
     *
     * @return The base for the group id
     */
    public final String groupIdBase()
    {
        return groupIdBase;
    }

    /**
     * Get the uniquifying string that ensures group ids do not collide with
     * artifacts that may not have been cleaned up from previous runs.
     *
     * @return A string
     */
    public final String uid()
    {
        return uid;
    }

    /**
     * Get the path to a project, given an artifact id, if one exists.
     *
     * @param aid An artifact id
     * @return A path or null
     */
    public final Path pathOf(String aid)
    {
        return projects.pathOf(aid).get();
    }

    /**
     * Create a new <i>git clone</i> of this entire project tree, in a separate
     * directory on disk.
     *
     * @return A clone
     * @throws IOException if something goes wrong
     */
    public abstract T newClone() throws IOException;

    /**
     * Build the project using mvn clean install.
     *
     * @return true if the maven project exits with 0
     */
    @Override
    public final boolean build()
    {
        return runMaven("clean", "install");
    }

    @Override
    public final boolean runMaven(String... args)
    {
        MavenCommand cmd = new MavenCommand(projects.cloneRoot(), args);
        return cmd.run().awaitQuietly(TIMEOUT);
    }

    protected List<String> sortedMatches(String aid)
    {
        List<String> result = new ArrayList<>();
        poms()
                .forEach(pom ->
                {
                    result.add(pom.artifactId().text());
                });
        LevenshteinDistance.sortByDistance(aid, result);
        return result;
    }

    @Override
    public T owner()
    {
        return cast();
    }

    @Override
    public Path path()
    {
        return projects.cloneRoot();
    }

    @Override
    public Pom pom()
    {
        return pom == null
               ? pom = ProjectWrapper.super.pom()
               : pom;
    }

    @Override
    public ArtifactIdentifiers identifiers()
    {
        return pom().toArtifactIdentifiers();
    }

    public Path root()
    {
        return projects.clones.workspaceClone();
    }

    public Path parentRoot()
    {
        return root().getParent();
    }

    protected Path localRepo()
    {
        String prop = System.getProperty("maven.repo.local");
        if (prop != null)
        {
            Path dir = Paths.get(prop);
            if (Files.exists(dir))
            {
                return dir;
            }
        }
        return Paths.get(System.getProperty("user.home")).resolve(".m2")
                .resolve("repository");
    }

    public void cleanup() throws IOException
    {
        String gidRelativePath = groupIdBase().replace('.', '/');
        Path m2artifacts = localRepo().resolve(gidRelativePath);
        FileUtils.deltree(parentRoot());
        if (Files.exists(m2artifacts))
        {
            FileUtils.deltree(m2artifacts);
        }
    }

    public static String uniquifier()
    {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString(
                Math.abs(ThreadLocalRandom.current()
                        .nextInt(2048)), 36);
    }

    public static String cactusVersion()
    {
        if (cactusVersion != null)
        {
            return cactusVersion;
        }
        String result = BuildMetadata.of(RunPolicy.class).projectProperties()
                .get("project-version");
        if (result == null)
        {
            throw new AssertionError("Could not locate cactus version info");
        }
        return cactusVersion = result;
    }

}
