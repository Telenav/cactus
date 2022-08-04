package com.telenav.cactus.maven.model.published;

import com.telenav.cactus.maven.model.DiskResident;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Can test against maven central if an artifact was published by comparing its
 * pom with any published one.
 *
 * @author Tim Boudreau
 */
public class PublishChecker
{
    private static final String DEFAULT_REPO = "https://repo1.maven.org/maven2/";
    private static HttpClient client;
    private final String baseUrl;

    private static synchronized HttpClient client()
    {
        return client == null
               ? (client = HttpClient.newHttpClient())
               : client;
    }

    public PublishChecker(String repo)
    {
        this.baseUrl = repo;
    }

    public PublishChecker()
    {
        this(DEFAULT_REPO);
    }

    public <A extends MavenArtifactCoordinates & DiskResident> PublishedState check(
            A project) throws IOException, InterruptedException, URISyntaxException
    {
        return check(baseUrl, project);
    }

    public <A extends MavenArtifactCoordinates & DiskResident> PublishedState check(
            String urlBase, A project) throws IOException, InterruptedException, URISyntaxException
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

    private <A extends MavenArtifactCoordinates & DiskResident> URL downloadUrl(
            String urlBase, A project) throws MalformedURLException
    {
        return new URL(
                urlBase + project.groupId().text().replace('.', '/') + '/' + project
                .artifactId() + '/' + project.version() + '/' + project
                .artifactId() + "-" + project.version() + ".pom");
    }

    private <A extends MavenArtifactCoordinates & DiskResident> String pomText(
            A project) throws IOException
    {
        return new String(Files.readAllBytes(project.path()), UTF_8)
                .trim();
    }

}
