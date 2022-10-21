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

import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.published.PublishChecker;
import com.telenav.cactus.maven.model.published.PublishedState;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PROPERTIES;
import static java.lang.String.join;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Avoids attempting to re-publish projects which have already been published,
 * where the POM on maven central is identical to the one which would be
 * published, by setting skip properties that are used by the maven deploy, gpg
 * and nexus deploy.
 * <p>
 * Use this mojo in targets or profiles that will really attempt to publish to
 * Maven central, <i>after</i> versions of things have been incremented. It will
 * fail if the pom for a project has <i>already been published</i> and the
 * published pom's contents differ from the pom you have locally.
 * </p>
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = INITIALIZE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "filter-published", threadSafe = true)
@BaseMojoGoal("filter-published")
public class FilterDeployingAlreadyPublishedMojo extends BaseMojo
{

    private static final SharedDataKey<Map<MavenArtifactCoordinates, PublishedState>> CACHE_KEY = SharedDataKey
            .of("published", Map.class);

    // PENDING: Provide a way to use a private repo
    private static final PublishChecker CHECKER = new PublishChecker();

    /**
     * Properties that should be set to "true" <i>in addition to the defaults of
     * <ul>
     * <li>maven.deploy.skip</li>
     * <li>gpg.skip</li>
     * <li>do.not.publish</li>
     * <li>skipNexusStagingDeployMojo</li>
     * </ul>
     */
    @Parameter(property = PROPERTIES)
    private String properties;

    /**
     * If true, disable this mojo for anything but projects with the packaging
     * pom.
     */
    @Parameter(property = "cactus.filter.pom.projects.only")
    private boolean pomProjectsOnly;

    private PublishedState publishedStateOf(MavenProject project)
    {
        // Since a mojo can be touched multiple times in a run, ensure we
        // only go out on the web and pull down the last released pom once.
        return quietly(() ->
        {
            Map<MavenArtifactCoordinates, PublishedState> cache = sharedData()
                    .computeIfAbsent(CACHE_KEY, ConcurrentHashMap::new);
            ThrowingSupplier<PublishedState> ts = () ->
            {
                return CHECKER.check(toPom(project)).state();
            };
            return cache.computeIfAbsent(coordinatesOf(project), key -> ts
                    .asSupplier().get());
        });
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (pomProjectsOnly && !"pom".equals(project.getPackaging()))
        {
            return;
        }
        PublishedState state = publishedStateOf(project);
        switch (state)
        {
            case NOT_PUBLISHED:
                break;
            case PUBLISHED_IDENTICAL:
                applyProperties(project, log);
                break;
            case PUBLISHED_DIFFERENT:
                // If we get this state, then we are about to try to publish
                // something on maven central that cannot do anything but fail,
                // so abort here, before we've invested time in completing the
                // build and uploading.
                fail("You are attempting to re-publish " + coordinatesOf(project)
                        + ". Its pom.xml locally differs from that already published. "
                        + " You need to bump its version and any usages of it "
                        + "before it can be published.");
        }
    }

    private void applyProperties(MavenProject project, BuildLog log)
    {
        Properties props = project.getProperties();
        Set<String> apply = propertiesToApply();
        if (isVerbose())
        {
            log.info(
                    "Set the following properties to 'true' for " + coordinatesOf(
                            project)
                    + ": " + join(", ", apply));
        }
        for (String prop : apply)
        {
            props.setProperty(prop, "true");
        }
    }

    private Set<String> propertiesToApply()
    {
        Set<String> result = new TreeSet<>();
        result.add("maven.deploy.skip");
        result.add("gpg.skip");
        result.add("skipNexusStagingDeployMojo");
        result.add("do.not.publish");
        if (properties != null)
        {
            for (String prop : properties.split("[, ]"))
            {
                prop = prop.trim();
                if (!prop.isEmpty())
                {
                    result.add(prop);
                }
            }
        }
        return result;
    }
}
