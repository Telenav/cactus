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
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.dependencies.Dependencies;
import com.telenav.cactus.maven.model.dependencies.DependencyScope;
import com.telenav.cactus.maven.model.dependencies.DependencySet;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.property.CoordinatesPropertyResolver;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableList;

/**
 * A PomResolver which is a collection of known pom.xml files on disk.
 *
 * @author Tim Boudreau
 */
public class Poms implements PomResolver
{
    private final List<Pom> sorted;
    private final Map<String, Map<String, Pom>> poms = new HashMap<>();
    private final Map<Pom, DependencySet> dependenciesContext = new ConcurrentHashMap<>();

    public Poms(Collection<? extends Pom> all)
    {
        sorted = new ArrayList<>(all);
        Collections.sort(sorted);
        for (Pom pom : sorted)
        {
            Map<String, Pom> kids
                    = poms.computeIfAbsent(pom.coords.groupId,
                            gid -> new HashMap<>());
            kids.put(pom.coords.artifactId, pom);
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Pom pom : sorted)
        {
            sb.append(pom.coords).append(", ");
        }
        return sb.toString();
    }

    public Dependencies dependencies(Pom pom)
    {
        return new DependencySet(pom, this.withLocalRepository(),
                dependenciesContext);
    }

    public List<Pom> poms()
    {
        return unmodifiableList(sorted);
    }

    public List<Pom> javaProjects()
    {
        List<Pom> result = new ArrayList<>();
        for (Pom p : sorted)
        {
            if (!"pom".equals(p.packaging))
            {
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public PropertyResolver propertyResolver(Pom pom)
    {
        PomResolver pomRes = this.or(PomResolver.local());
        return pomRes.propertyResolver(pom)
                .or(new CoordinatesPropertyResolver(pom, pomRes))
                .memoizing();
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        if ("---".equals(version))
        {
            throw new IllegalStateException(
                    "Wrong get: " + groupId + ":" + artifactId);
        }
        return get(groupId, artifactId).flatMap(pom
                -> version.equals(pom.coords.version)
                   ? Optional.of(pom)
                   : Optional.empty());
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId)
    {
        Map<String, Pom> map = poms.get(groupId);
        if (map == null)
        {
            return ThrowingOptional.empty();
        }
        Pom result = map.get(artifactId);
        return ThrowingOptional.ofNullable(result);
    }

    public static Poms in(Path dir) throws IOException
    {
        List<Pom> list = new ArrayList<>();
        try ( Stream<Path> all = Files.walk(dir).filter(file -> !Files
                .isDirectory(file) && "pom.xml".equals(file.getFileName()
                .toString())))
        {
            for (Path p : all.collect(Collectors.toCollection(ArrayList::new)))
            {
                Pom.from(p).ifPresent(list::add);
            }
        }
        if (list.isEmpty())
        {
            throw new IOException("No poms in " + dir);
        }
        return new Poms(list);
    }

    public static void main(String[] args) throws Exception
    {
//        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
        Poms poms = Poms.in(Paths.get(
                "/Users/timb/work/personal/mastfrog-parent"));
//        Pom p = poms.get("com.telenav.cactus", "cactus-maven-plugin").get();
//        Pom p = poms.get("com.telenav.mesakit", "mesakit-geocoding").get();
        Pom p = poms.get("com.mastfrog", "acteur-resources").get();

        PomFile.of(p).visitDependencies(false, dep ->
        {
            System.out.println(" * " + dep + (dep.isResolved()
                                              ? " RESOLVED"
                                              : ""));
        });

        DependencySet set = new DependencySet(p, poms.withLocalRepository()
                .memoizing());

        System.out.println("DDs");
        for (Dependency d : set.directDependencies(false,
                DependencyScope.Compile
                        .asSet()))
        {
            System.out.println(" * " + d);
        }

        System.out.println("\nFULL");
        for (Dependency d : set
                .dependencyClosure(false, DependencyScope.Compile))
        {
            System.out.println(" * " + d);
        }

    }
}
