package com.telenav.cactus.maven.publishcheck;

import com.telenav.cactus.maven.shared.SharedData;
import com.telenav.cactus.maven.shared.SharedDataKey;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author timb
 */
@Singleton
public class PublishChecker
{
    private static final String DEFAULT_REPO = "https://repo1.maven.org/maven2/";
    private static final SharedDataKey<HttpClient> HTTP_CLIENT_KEY = SharedDataKey
            .of(HttpClient.class);

    @Inject
    SharedData sharedData;

    private HttpClient client()
    {
        return sharedData.computeIfAbsent(HTTP_CLIENT_KEY,
                HttpClient::newHttpClient);
    }

    public PublishedState check(MavenProject project) throws IOException, InterruptedException, URISyntaxException
    {
        return check(DEFAULT_REPO, project);
    }

    public PublishedState check(String urlBase, MavenProject project) throws IOException, InterruptedException, URISyntaxException
    {
        HttpRequest request = HttpRequest.newBuilder(downloadUrl(urlBase,
                project)
                .toURI()).GET().timeout(
                        Duration.ofSeconds(60)).build();
        HttpResponse<String> response = client().send(request,
                HttpResponse.BodyHandlers
                        .ofString(UTF_8));
        switch (response.statusCode())
        {
            case 500: // ?
            case 404:
            case 410:
                return PublishedState.NOT_PUBLISHED;
            default:
                if (response.statusCode() >= 200 && response.statusCode() < 299)
                {
                    String localText = pomText(project);
                    String remoteText = response.body().trim();
                    if (localText.equals(remoteText))
                    {
                        return PublishedState.PUBLISHED_IDENTICAL;
                    }
                    return PublishedState.PUBLISHED_DIFFERENT;
                }
                return PublishedState.NOT_PUBLISHED;
        }
    }

    private URL downloadUrl(String urlBase, MavenProject project) throws MalformedURLException
    {
        return new URL(
                urlBase + project.getGroupId().replace('.', '/') + '/' + project
                .getArtifactId() + '/' + project.getVersion() + '/' + project
                .getArtifactId() + "-" + project.getVersion() + ".pom");
    }

    private String pomText(MavenProject project) throws IOException
    {
        return new String(Files.readAllBytes(project.getFile().toPath()), UTF_8)
                .trim();
    }

}
