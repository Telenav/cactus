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
package com.telenav.cactus.maven.model.resolver;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.threadlocal.ThreadLocalValue;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.versions.VersionComparator;
import com.telenav.cactus.maven.model.resolver.versions.VersionMatchers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves against the local maven repository.
 *
 * PENDING: Add a way to supply a function that will download not-found
 * dependencies.
 *
 * @author Tim Boudreau
 */
final class LocalRepoResolver implements PomResolver
{
    private static Path localRepo;
    private final ThreadLocalValue<ArtifactFinder> finder = ThreadLocalValue.create();
    static LocalRepoResolver INSTANCE = new LocalRepoResolver();
    
    void withArtifactFinder(ArtifactFinder finder, Runnable run) {
        this.finder.withValue(finder, run);
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        Supplier<ThrowingOptional<Pom>> supp = () -> 
            inLocalRepository(groupId, artifactId, version);
    
        ThrowingOptional<Pom> result = supp.get();
        Optional<ArtifactFinder> finderOpt = finder.toOptional();
        if (!result.isPresent() && finderOpt.isPresent()) {
            ArtifactFinder af = finderOpt.get();
            if (af.find(groupId, artifactId, version, "jar").isPresent()) {
                result = supp.get();
            }
        }
        return result;
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
            String artifactId, String version)
    {
        if (VersionMatchers.looksLikeRange(version))
        {
            ThrowingOptional<Pom> result = inLocalRepository(groupId, artifactId,
                    VersionMatchers.matcher(version));
            if (result.isPresent())
            {
                return result;
            }
        }
        return firstExistingPom(groupId, artifactId, version);
    }

    private static ThrowingOptional<Pom> firstExistingPom(String groupId,
            String artifactId, String version)
    {
        Path result = firstExisting(() -> localRepository().resolve(
                localRepositoryFolderPath(groupId,
                        artifactId, version).resolve(
                                pomName(artifactId, version))),
                () -> localRepository().resolve(altLocalRepositoryFolderPath(
                        groupId, artifactId, version)
                        .resolve(pomName(artifactId, version))));
        if (result == null)
        {
//            System.out.println("DID NOT FIND: ");
//            System.out.println("  " + localRepository().resolve(
//                    localRepositoryFolderPath(groupId,
//                            artifactId, version).resolve(
//                                    pomName(artifactId, version))) + " or");
//            System.out.println("  " + localRepository().resolve(
//                    altLocalRepositoryFolderPath(groupId,
//                            artifactId, version).resolve(
//                                    pomName(artifactId, version))) + " or");
//  
            return inLocalRepository(groupId, artifactId,
                    VersionMatchers.matcher(version));
        }
        return Pom.from(result);
    }

    private static ThrowingOptional<Pom> inLocalRepository(String groupId,
            String artifactId, Predicate<String> versionAccepter)
    {
        Path par = localRepositoryParent(groupId, artifactId);
        if (!Files.exists(par))
        {
            par = altLocalRepositoryParent(groupId, artifactId);
        }
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
        Collections.sort(result, VersionComparator.INSTANCE);
        String ver = result.getLast();
        return inLocalRepository(groupId, artifactId, ver);
    }

    private static Path localRepositoryParent(String groupId,
            String artifactId)
    {
        return localRepository().resolve(pathify(groupId))
                .resolve(pathify(artifactId));
    }

    private static Path altLocalRepositoryParent(String groupId,
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

    private static Path altLocalRepositoryFolderPath(String groupId,
            String artifactId, String version)
    {
        // For some reason, javax.inject winds up in a folder named
        // javax.inject instead of javax/inject.  Maybe an artifact of its
        // version being "1"?
        return pathify(groupId).resolve(artifactId)
                .resolve(version);
    }

    private static String reducedVersion(String version)
    {
        int ix = version.lastIndexOf('.');
        if (ix > 0)
        {
            return version.substring(0, ix);
        }
        return version;
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
                    if (result != null)
                    {
                        result = result.resolve(parts[i]);
                    }
                    else
                    {
                        result = Paths.get(parts[i]);
                    }
            }
        }
        return result;
    }

    @SafeVarargs
    private static Path firstExisting(Supplier<Path>... suppliers)
    {
        Set<Path> seen = new HashSet<>();
        for (Supplier<Path> s : suppliers)
        {
            Path result = s.get();
            if (seen.add(result))
            {
                if (result != null && Files.exists(result))
                {
                    return result;
                }
            }
        }
        return null;
    }
}
