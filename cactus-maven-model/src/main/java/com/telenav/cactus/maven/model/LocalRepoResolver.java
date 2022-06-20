package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.telenav.cactus.maven.model.Pom.from;

/**
 *
 * @author Tim Boudreau
 */
final class LocalRepoResolver implements PomResolver
{
    private static Path localRepo;
    static LocalRepoResolver INSTANCE = new LocalRepoResolver();

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId, String version)
    {
        return inLocalRepository(groupId, artifactId, version);
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId)
    {
        return inLocalRepository(groupId, artifactId, ver -> !ver.endsWith(
                "-SNAPSHOT"))
                .or(inLocalRepository(groupId, artifactId, ver -> ver
                .endsWith("-SNAPSHOT")));
    }

    @Override
    public String toString()
    {
        return "Local(" + localRepository() + ")";
    }

    private static Path localRepository()
    {
        return localRepo = firstExisting(() -> localRepo,
                () ->
        {
            String propValue = System.getProperty("maven.local.repo");
            if (propValue != null)
            {
                Path result = Paths.get(propValue);
                return result;
            }
            return null;

        }, () ->
        {
            String opts = System.getenv("MAVEN_OPTS");
            if (opts != null)
            {
                // XX does not handle space in path
                Pattern p = Pattern.compile("^.*?-Dmaven.repo.local=(\\S+).*");
                Matcher m = p.matcher(opts);
                if (m.find())
                {
                    Path result = Paths.get(m.group(1));
                    return result;
                }
            }
            return null;
        }, () ->
        {
            String home = System.getProperty("user.home");
            if (home != null)
            {
                return Paths.get(home).resolve(".m2").resolve("repository");
            }
            return null;
        }, () ->
        {
            String home = System.getenv("HOME");
            if (home != null)
            {
                return Paths.get(home).resolve(".m2").resolve("repository");
            }
            return null;
        });
    }

    private static ThrowingOptional<Pom> inLocalRepository(String groupId,
            String artfactId, String version)
    {
        return from(localRepository().resolve(
                localRepositoryFolderPath(groupId, artfactId, version))
                .resolve(pomName(artfactId, version)));
    }

    private static ThrowingOptional<Pom> inLocalRepository(String groupId,
            String artifactId, Predicate<String> versionAccepter)
    {
        Path par = localRepositoryParent(groupId, artifactId);
        if (!Files.exists(par))
        {
            return ThrowingOptional.empty();
        }
        LinkedList<String> result = new LinkedList<>();
        String[] names = par.toFile().list();
        for (String n : names)
        {
            if (versionAccepter.test(n))
            {
                Path p = par.resolve(n).resolve(pomName(artifactId, n));
                if (Files.exists(p))
                {
                    result.add(n);
                }
            }
        }
        if (result.isEmpty())
        {
            return ThrowingOptional.empty();
        }
        Collections.sort(result);
        String ver = result.getLast();
        if ("3.0.0-dev".equals(ver)) {
            throw new IllegalStateException("Gotcha: " + groupId + ":" + artifactId);
        }
        return inLocalRepository(groupId, artifactId, ver);
    }

    private static Path localRepositoryParent(String groupId,
            String artifactId)
    {
        return localRepository().resolve(pathify(groupId))
                .resolve(pathify(artifactId));
    }

    private static Path pomName(String artifactId, String version)
    {
        return artifactName(artifactId, version, "pom");
    }

    private static Path artifactName(String artifactId, String version,
            String type)
    {
        return Paths.get(artifactId + "-" + version + "." + type);
    }

    private static Path localRepositoryFolderPath(String groupId,
            String artifactId, String version)
    {
        return pathify(groupId).resolve(pathify(artifactId))
                .resolve(version);
    }

    private static Path pathify(String mavenCoordinatesPart)
    {
        String[] parts = mavenCoordinatesPart.split("[\\./]");
        Path result = null;
        for (int i = 0; i < parts.length; i++)
        {
            switch (i)
            {
                case 0:
                    result = Paths.get(parts[i]);
                    break;
                default:
                    result = result.resolve(parts[i]);
            }
        }
        return result;
    }

    @SafeVarargs
    private static Path firstExisting(Supplier<Path>... suppliers)
    {
        for (Supplier<Path> s : suppliers)
        {
            Path result = s.get();
            if (result != null && Files.exists(result))
            {
                return result;
            }
        }
        return null;
    }
}
