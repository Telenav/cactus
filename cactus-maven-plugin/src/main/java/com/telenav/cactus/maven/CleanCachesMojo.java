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

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.scope.ProjectFamily;
import com.telenav.cactus.util.PathUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Cleans any cache dirs for the project or projects.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.CLEAN,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "clean-caches", threadSafe = true)
public class CleanCachesMojo extends BaseMojo
{
    private static final Set<Path> seen = ConcurrentHashMap.newKeySet();

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
            throw new MojoExecutionException(CacheFindingStrategy.class, msg,
                    msg);
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
                    return PathUtils.userCacheRoot().resolve(project
                            .getGroupId());
                default:
                    throw new AssertionError(this);
            }
        }

        public void deleteCache(MavenProject project, BuildLog log,
                                Set<Path> seen) throws IOException
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
            }
            else
            {
                log.info("Delete cache dir " + dir);
                PathUtils.deleteWithSubtree(dir);
            }
        }
    }

    @Parameter(property = "cactus.cache-finding-strategy",
               defaultValue = "byGroupIdVersion")
    private String cacheFindingStrategy;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        CacheFindingStrategy strategy = CacheFindingStrategy.find(
                cacheFindingStrategy);
        strategy.deleteCache(project, log, seen);
    }
}
