package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.util.PathUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugin.MojoExecutionException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Cleans any cache dirs for the project or projects.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.CLEAN,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "clean-caches", threadSafe = true)
public class CleanCachesMojo extends BaseMojo
{

    @Parameter(property = "cacheFindingStrategy", defaultValue = "byGroupIdVersion")
    private String cacheFindingStrategy;

    private static final Set<Path> seen = ConcurrentHashMap.newKeySet();

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        CacheFindingStrategy strategy = CacheFindingStrategy.find(cacheFindingStrategy);
        strategy.deleteCache(project, log, seen);
    }

    public static enum CacheFindingStrategy
    {
        temporary,
        byFamily,
        byFamilyAndVersion,
        byFamiilyArtifactIdVersion;

        public static CacheFindingStrategy find(String injected) throws MojoExecutionException
        {
            if (injected == null)
            {
                return byFamilyAndVersion;
            }
            for (CacheFindingStrategy strategy : values())
            {
                if (strategy.name().equalsIgnoreCase(injected))
                {
                    return strategy;
                }
            }
            String msg = "Requested strategy '" + injected + " is not one of "
                    + Arrays.toString(values());
            throw new MojoExecutionException(CacheFindingStrategy.class, msg, msg);
        }

        public Path cacheDir(MavenProject project)
        {
            switch (this)
            {
                case temporary:
                    return PathUtils.temp()
                            .resolve(ProjectFamily.of(project).name())
                            .resolve(project.getVersion());
                case byFamilyAndVersion:
                    return PathUtils.userCacheRoot()
                            .resolve(ProjectFamily.of(project).name())
                            .resolve(project.getVersion());
                case byFamiilyArtifactIdVersion:
                    return PathUtils.userCacheRoot()
                            .resolve(ProjectFamily.of(project).name())
                            .resolve(project.getArtifactId())
                            .resolve(project.getVersion());
                case byFamily:
                    return PathUtils.userCacheRoot().resolve(project.getGroupId());
                default:
                    throw new AssertionError(this);
            }
        }

        public void deleteCache(MavenProject project, BuildLog log, Set<Path> seen) throws IOException
        {
            Path dir = cacheDir(project);
            if (seen.contains(dir))
            {
                return;
            }
            log = log.child("deleteCacheWithSubtree").child(name());
            if (!Files.exists(dir))
            {
                log.info("Cache dir does not exist:"
                        + dir).info("Doing nothing.");
            } else
            {
                log.info("Delete cache dir " + dir);
                PathUtils.deleteWithSubtree(dir);
            }
        }
    }
}