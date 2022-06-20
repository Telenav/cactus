package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.unmodifiableList;

/**
 *
 * @author Tim Boudreau
 */
public class Poms implements PomResolver
{
    private final List<Pom> sorted;
    private final Map<String, Map<String, Pom>> poms = new HashMap<>();

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
    
    public List<Pom> poms() {
        return unmodifiableList(sorted);
    }

    @Override
    public PropertyResolver propertyResolver(Pom pom) throws Exception
    {
        return this.or(LocalRepoResolver.INSTANCE).propertyResolver(pom)
                .or(new CoordinatesPropertyResolver(pom))
                .memoizing();
    }
    
    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        if ("---".equals(version)) {
            throw new IllegalStateException("Wrong get: " + groupId + ":" + artifactId);
        }
        return get(groupId, artifactId).flatMap(pom
                -> version.equals(pom.coords.version)
                   ? Optional.of(pom)
                   : Optional.empty());
    }

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
        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
//        Pom p = poms.get("com.telenav.cactus", "cactus-maven-plugin").get();
        Pom p = poms.get("com.telenav.mesakit", "mesakit-geocoding").get();

        p.toPomFile().visitDependencies(false, dep ->
        {
            System.out.println(" * " + dep + (dep.isResolved()
                                              ? " RESOLVED"
                                              : ""));
        });

        DependencySet set = new DependencySet(p, poms);

        System.out.println("DDs");
        for (Dependency d : set.directDependencies(DependencyScope.Compile
                .asSet()))
        {
            System.out.println(" * " + d);
        }

        System.out.println("\nFULL");
        for (Dependency d : set.fullDependencies(DependencyScope.Compile
                .asSet()))
        {
            System.out.println(" * " + d);
        }

    }
}
