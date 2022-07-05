package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.SharedDataMojo;
import com.telenav.cactus.maven.model.published.PublishChecker;
import com.telenav.cactus.maven.model.published.PublishedState;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.net.http.HttpClient;
import javax.inject.Inject;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;

/**
 * Before publishing a release, check if there is already a version of the
 * project published on maven central, and if it is, see if its pom differs. If
 * it doesn't differ, inject skipStaging into the project's properties. If it
 * does differ, fail the build because it needs its version bumped.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "check-published", threadSafe = true)
public class CheckAlreadyPublishedMojo extends SharedDataMojo
{
    private static final SharedDataKey<HttpClient> HTTP_CLIENT_KEY = SharedDataKey
            .of(HttpClient.class);

    @Parameter(property = "cactus.url.base",
            defaultValue = "https://repo1.maven.org/maven2/")
    private String urlBase = "https://repo1.maven.org/maven2/";

    @Parameter(property = "cactus.published.warn", defaultValue = "false")
    private boolean warnOnAlreadyPublished;

    @Parameter(property = "cactus.publish.check.skip", required = false)
    private boolean skip;

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

        PublishedState state = checker.check(MavenArtifactCoordinatesWrapper
                .wrap(project));
        switch (state)
        {
            case NOT_PUBLISHED:
                log.info("Not already published: " + project.getArtifactId());
                break;
            case PUBLISHED_IDENTICAL:
                break;
            case PUBLISHED_DIFFERENT:
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
}
