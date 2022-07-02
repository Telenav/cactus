package com.telenav.cactus.maven;

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.SharedDataMojo;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.time.Duration;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;
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
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "check-published", threadSafe = true)
public class CheckAlreadyPublishedMojo extends SharedDataMojo
{
    private static final SharedDataKey<HttpClient> HTTP_CLIENT_KEY = SharedDataKey
            .of(HttpClient.class);
    
    @Parameter(property="cactus.url.base", defaultValue="https://repo1.maven.org/maven2/")
    private String urlBase = "https://repo1.maven.org/maven2/";
    
    @Parameter(property="cactus.published.warn", defaultValue="false")
    private boolean warnOnAlreadyPublished;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        log
                .info("Check if " + project.getArtifactId() + " is already published");
        HttpRequest request = HttpRequest.newBuilder(downloadUrl(project)
                .toURI()).GET().timeout(
                        Duration.ofSeconds(60)).build();
        HttpResponse<String> response = client().send(request, BodyHandlers
                .ofString(UTF_8));
        switch (response.statusCode())
        {
            case 500: // ?
            case 404:
            case 410:
                log.info("Not already published: " + project.getArtifactId());
                break;
            default:
                if (response.statusCode() >= 200 && response.statusCode() < 299)
                {
                    log.info(
                            "Already published: " + project.getArtifactId() + " - diffing.");
                    checkTextMatches(response, project, log);
                }
        }
    }

    private HttpClient client()
    {
        return sharedData().computeIfAbsent(HTTP_CLIENT_KEY,
                HttpClient::newHttpClient);
    }

    private URL downloadUrl(MavenProject project) throws MalformedURLException
    {
        return new URL(
                urlBase + project.getGroupId().replace('.', '/') + '/' + project
                .getArtifactId() + '/' + project.getVersion() + '/' + project
                .getArtifactId() + "-" + project.getVersion() + ".pom");
    }

    private void checkTextMatches(HttpResponse<String> response,
            MavenProject project, BuildLog log) throws IOException, MojoExecutionException
    {
        String localText = pomText(project);
        String remoteText = response.body().trim();
        if (!localText.equals(remoteText))
        {
            String msg = "POM for " + project.getGroupId() + ":" + project
                    .getArtifactId() + ":" + project.getVersion()
                    + " was already published, and the contents differs from the local copy.  "
                    + "Its version needs to be bumped.";
            if (warnOnAlreadyPublished) {
                log.warn(msg);
            } else {
                fail(msg);
            }
        }
        else
        {
            log.info(
                    "The already published pom for " + project.getGroupId() + ":" + project
                    .getArtifactId() + ":" + project.getVersion() + " is identical to what "
                    + "we would publish - setting skipStaging to true.");
            project.getProperties().setProperty("skipStaging", "true");
            project.getProperties().setProperty("skipNexusStagingDeployMojo",
                    "true");
            project.getProperties().setProperty("skipRemoteStaging", "true");
        }
    }

    private String pomText(MavenProject project) throws IOException
    {
        return new String(Files.readAllBytes(project.getFile().toPath()), UTF_8)
                .trim();
    }
}
