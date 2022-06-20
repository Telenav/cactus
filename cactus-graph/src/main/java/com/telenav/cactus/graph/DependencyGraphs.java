package com.telenav.cactus.graph;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.IntGraphBuilder;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.DependencyScope;
import com.telenav.cactus.maven.model.DependencySet;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomResolver;
import com.telenav.cactus.maven.model.Poms;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static com.telenav.cactus.maven.model.DependencyScope.Compile;
import static java.util.Collections.unmodifiableList;

/**
 *
 * @author Tim Boudreau
 */
public class DependencyGraphs implements Iterable<Pom>
{

    private final Poms poms;
    private final Map<Pom, DependencySet> sets = new HashMap<>();
    private final List<Pom> targets;
    private final PomResolver resolver;

    public DependencyGraphs(Collection<? extends Pom> poms)
    {
        this.poms = new Poms(poms);
        targets = new ArrayList<>(poms);
        Collections.sort(targets);
        resolver = this.poms.or(PomResolver.local()).memoizing();
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId)
    {
        return poms.get(groupId, artifactId);
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        return poms.get(groupId, artifactId);
    }

    public ThrowingOptional<ObjectGraph<MavenCoordinates>> dependencies(
            String groupId, String artifactId, DependencyScope... scopes)
    {
        greaterThanZero("scopes.length", scopes.length);
        return get(groupId, artifactId).map(pom ->
        {
            return new DepsTraverser(DependencyScope.set(scopes)).go(pom);
        });
    }

    class DepsTraverser
    {
        private final Map<MavenCoordinates, Set<MavenCoordinates>> deps = new HashMap<>();
        private final Set<MavenCoordinates> all = new HashSet<>();
        private final Set<DependencyScope> scopes;
        private final Set<DependencyScope> transitiveScopes = EnumSet.noneOf(
                DependencyScope.class);

        public DepsTraverser(Set<DependencyScope> scopes)
        {
            this.scopes = scopes;
            for (DependencyScope d : scopes)
            {
                transitiveScopes.addAll(d.transitivity());
            }
        }

        public ObjectGraph<MavenCoordinates> go(Pom target)
        {
            go(0, target);
            List<MavenCoordinates> sorted = new ArrayList<>(all);
            Collections.sort(sorted);
            IntGraphBuilder ib = IntGraph.builder(sorted.size());
            deps.forEach((lib, libDependencies) ->
            {
                int ix = sorted.indexOf(lib);
                libDependencies.forEach(dep ->
                {
                    int dix = sorted.indexOf(dep);
                    ib.addEdge(ix, dix);
                });
            });
            return ib.build().toObjectGraph(sorted);
        }

        private Set<DependencyScope> scopes(int depth)
        {
            return depth == 0
                   ? scopes
                   : transitiveScopes;
        }

        private void go(int depth, Pom target)
        {
            MavenCoordinates targetCoords = target.coords
                    .toPlainMavenCoordinates();
            if (deps.containsKey(targetCoords))
            {
                return;
            }
            all.add(targetCoords);
            DependencySet set = dependencySet(target);
            Set<Dependency> directDeps = set.directDependencies(scopes(depth));
            Set<MavenCoordinates> coordinateSet = toCoordinates(directDeps);
            all.addAll(coordinateSet);
            this.deps.put(targetCoords.toPlainMavenCoordinates(),
                    coordinateSet);

            for (Dependency d : directDeps)
            {
                if (!d.isResolved())
                {
                    System.out.println(
                            " HAVE UNRESOLVED FOR " + target + ": " + d);
                }
                MavenCoordinates depCoords = d.coords.toPlainMavenCoordinates();
                if (this.deps.containsKey(depCoords))
                {
                    continue;
                }
                resolver.get(d).ifPresent(child -> go(depth + 1, child));
            }
        }

        private Set<MavenCoordinates> toCoordinates(
                Collection<? extends Dependency> dep)
        {
            Set<MavenCoordinates> result = new HashSet<>();
            dep.forEach(d -> result.add(d.coords.toPlainMavenCoordinates()));
            return result;
        }
    }

    private DependencySet dependencySet(Pom pom)
    {
        if (!sets.containsKey(pom))
        {
            try
            {
                DependencySet result = new DependencySet(pom, poms, sets);
                sets.put(pom, result);
                return result;
            }
            catch (Exception ex)
            {
                return Exceptions.chuck(ex);
            }
        }
        else
        {
            return sets.get(pom);
        }
    }

    public ObjectGraph<MavenCoordinates> parentage()
    {
        Set<MavenCoordinates> allCoordinates = new TreeSet<>();
        Map<MavenCoordinates, MavenCoordinates> all = new HashMap<>();
        ParentsCollector col = new ParentsCollector(resolver, all);
        for (Pom p : targets)
        {
            col.go(p);
        }
        allCoordinates.addAll(all.keySet());
        allCoordinates.addAll(all.values());
        List<MavenCoordinates> sorted = new ArrayList<MavenCoordinates>(
                allCoordinates);
        IntGraphBuilder ib = IntGraph.builder(allCoordinates.size());
        ib.addOrphan(all.size() - 1);
        all.forEach((child, par) ->
        {
            int ixc = sorted.indexOf(child);
            int ixp = sorted.indexOf(par);
            ib.addEdge(ixp, ixc);
        });
        return ib.build().toObjectGraph(sorted);
    }

    @Override
    public Iterator<Pom> iterator()
    {
        return unmodifiableList(targets).iterator();
    }

    private static final class ParentsCollector implements ThrowingConsumer<Pom>
    {
        private final Map<MavenCoordinates, MavenCoordinates> coords;
        private final PomResolver res;
        private MavenCoordinates curr;

        ParentsCollector(PomResolver res,
                Map<MavenCoordinates, MavenCoordinates> collectInto)
        {
            this.coords = collectInto;
            this.res = res;
        }

        public void go(Pom owner)
        {
            curr = owner.coords.toPlainMavenCoordinates();
            if (coords.containsKey(curr))
            {
                return;
            }
            owner.visitParents(res, this);
            curr = null;
        }

        @Override
        public void accept(Pom obj) throws Exception
        {
            MavenCoordinates parent = obj.coords.toPlainMavenCoordinates();
            if (curr.equals(parent))
            {
                throw new IllegalStateException("Pom parented to itself: " + obj);
            }
            coords.put(curr, parent);
            curr = parent;
        }
    }

    public static void main(String[] args) throws Exception
    {
        Poms poms = Poms.in(Paths.get("/Users/timb/work/telenav/jonstuff"));
        DependencyGraphs dg = new DependencyGraphs(poms.poms());

        ThrowingOptional<ObjectGraph<MavenCoordinates>> to = dg.dependencies(
                "com.telenav.cactus", "cactus-maven-plugin", Compile);
        to.ifPresent(graph ->
        {
            System.out.println("GRAPH WITH " + graph.size());
            System.out.println(graph);
        });
        /*
        ObjectGraph<MavenCoordinates> parentage = dg.parentage();
        List<Score<MavenCoordinates>> scores = parentage.eigenvectorCentrality();
        System.out.println("GRAPH:\n" + parentage);
        System.out.println("\nEigenvector Centrality:");
        scores.forEach(sc ->
        {
        if (sc.score() > 0.0000001)
        {
        System.out.println("  * " + sc.score() + "\t" + sc.node());
        }
        });
         */

    }
}
