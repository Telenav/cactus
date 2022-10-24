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
import com.telenav.cactus.maven.model.published.PublishChecker;
import com.telenav.cactus.maven.model.published.PublishedState;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.net.http.HttpClient;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.MavenArtifactCoordinatesWrapper.wrap;
import static com.telenav.cactus.maven.shared.SharedDataKey.of;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Before publishing a release, check if there is already a version of the
 * project published on maven central, and if it is, see if its pom differs. If
 * it doesn't differ, inject skipStaging into the project's properties. If it
 * does differ, fail the build because it needs its version bumped.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "check-published", threadSafe = true)
@BaseMojoGoal("check-published")
public class CheckAlreadyPublishedMojo extends BaseMojo
{
    private static final SharedDataKey<HttpClient> HTTP_CLIENT_KEY = of(
            HttpClient.class);

    @Parameter(property = "cactus.url.base",
            defaultValue = "https://repo1.maven.org/maven2/")
    private final String urlBase = "https://repo1.maven.org/maven2/";

    @Parameter(property = "cactus.published.warn", defaultValue = "false")
    private boolean warnOnAlreadyPublished;

    @Parameter(property = "cactus.publish.check.skip")
    private boolean skip;

    @Parameter(property = "cactus.identical-ok", defaultValue="true")
    private boolean identicalOk;

    @Inject
    private PublishChecker checker;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (skip)
        {
            log.info("Artifact publication check is skipped");
            return;
        }
        log.info("Check if " + project.getArtifactId()
                + " is already published");

        PublishedState state = checker.check(wrap(project)).state();
        switch (state)
        {
            case NOT_PUBLISHED:
                log.info("Not already published: " + project.getArtifactId());
                break;
            case PUBLISHED_IDENTICAL:
                if (!identicalOk)
                {
                    warnOrFail(log, project);
                }
                break;
            case PUBLISHED_DIFFERENT:
                warnOrFail(log, project);
        }
    }

    private void warnOrFail(BuildLog log, MavenProject project)
    {
        String msg = "POM for " + project.getGroupId() + ":" + project
                .getArtifactId() + ":" + project.getVersion()
                + " was already published, and the contents differs from the local copy.  "
                + "Its version needs to be bumped.";
        if (warnOnAlreadyPublished)
        {
            log.warn(msg);
        }
        else
        {
            fail(msg);
        }
    }
}
