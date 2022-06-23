package com.telenav.cactus.graph;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.graph.IntGraph;
import com.mastfrog.graph.IntGraphBuilder;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.graph.algorithm.Score;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.dependencies.DependencyScope;
import com.telenav.cactus.maven.model.dependencies.DependencySet;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import com.telenav.cactus.maven.model.resolver.Poms;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static com.mastfrog.util.preconditions.Checks.greaterThanZero;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableList;

/**
 * Factory for dependency graphs from maven poms.
 *
 * @author Tim Boudreau
 */
final class DependencyGraphs implements Iterable<Pom>
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
        resolver = this.poms.withLocalRepository().memoizing();
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId)
    {
        return poms
                .get(groupId, artifactId);
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
    {
        return poms.get(groupId, artifactId, version);
    }

    public ThrowingOptional<ObjectGraph<MavenCoordinates>> dependencyGraph(
            String groupId, String artifactId, DependencyScope... scopes)
    {
        greaterThanZero("scopes.length", scopes.length);
        return get(groupId, artifactId).map(pom ->
        {
            return new DT(DependencyScope.setOf(scopes), false).go(pom);
        });
    }

    public ObjectGraph<MavenCoordinates> dependencyGraph(
            Set<DependencyScope> scopes,
            Predicate<MavenCoordinates> postFilter, Pom first, Pom... anyMore)
    {
        Set<Pom> poms = new LinkedHashSet<>();
        poms.add(first);
        poms.addAll(Arrays.asList(anyMore));
        return dependencyGraph(scopes, false, null, postFilter, poms);
    }

    public ObjectGraph<MavenCoordinates> dependencyGraph(
            Set<DependencyScope> scopes, boolean includeOptionalDependencies,
            BiPredicate<Pom, Dependency> preFilter,
            Predicate<MavenCoordinates> postFilter,
            Collection<? extends Pom> poms)
    {
        if (scopes.isEmpty())
        {
            scopes = DependencyScope.all();
        }
        return new DT(scopes, includeOptionalDependencies, postFilter, preFilter)
                .go(poms);
    }

    class DT implements BiPredicate<Pom, Dependency>
    {

        private final Map<MavenCoordinates, Set<MavenCoordinates>> deps = new HashMap<>();
        private final Set<DependencyScope> scopes;
        private final Set<MavenCoordinates> all = new HashSet<>();
        private final Set<Pom> traversedPoms = new HashSet<>();
        private final boolean includeOptionalDependencies;
        private final Predicate<MavenCoordinates> postFilter;
        private final BiPredicate<Pom, Dependency> preFilter;

        public DT(Set<DependencyScope> scopes,
                boolean includeOptionalDependencies)
        {
            this(scopes, false, null, null);
        }

        private void postFilter()
        {
            if (postFilter != null)
            {
                Map<MavenCoordinates, Set<MavenCoordinates>> filtered = new HashMap<>();
                deps.forEach((coord, deps) ->
                {
                    if (postFilter.test(coord))
                    {
                        Set<MavenCoordinates> nue = new LinkedHashSet<>();
                        for (MavenCoordinates dep : deps)
                        {
                            if (postFilter.test(dep))
                            {
                                nue.add(dep);
                            }
                            else
                            {
                                all.remove(dep);
                            }
                        }
                        if (!nue.isEmpty())
                        {
                            filtered.put(coord, nue);
                        }
                    }
                    else
                    {
                        all.remove(coord);
                    }
                });
                // Orphans will still exist, if they had no dependencies - 
                // remove them
                List<MavenCoordinates> newAll = new ArrayList<>();
                for (MavenCoordinates mc : all)
                {
                    if (postFilter.test(mc))
                    {
                        newAll.add(mc);
                    }
                }
                all.clear();
                all.addAll(newAll);
                deps.clear();
                deps.putAll(filtered);
            }
        }

        public DT(Set<DependencyScope> scopes,
                boolean includeOptionalDependencies,
                Predicate<MavenCoordinates> postFilter,
                BiPredicate<Pom, Dependency> preFilter)
        {
            this.scopes = scopes;
            this.includeOptionalDependencies = includeOptionalDependencies;
            this.postFilter = postFilter;
            this.preFilter = preFilter;
        }

        @Override
        public boolean test(Pom t, Dependency u)
        {
            traversedPoms.add(t);
            if (preFilter == null || preFilter.test(t, u))
            {
                deps.compute(t.coords, (cds, set) ->
                {
                    if (set == null)
                    {
                        set = new TreeSet<>();
                    }
                    set.add(u.coords);
                    return set;
                });
                all.add(t.coords);
                all.add(u.coords);
            }
            return true;
        }

        public ObjectGraph<MavenCoordinates> go(Pom thePom)
        {
            return go(singleton(thePom));
        }

        public ObjectGraph<MavenCoordinates> go(
                Collection<? extends Pom> thePoms)
        {
            for (Pom other : thePoms)
            {
                // We may have already indirectly traversed a pom, in
                // which case we (probably - modulo test dependencies)
                // don't need to again
                if (!traversedPoms.contains(other))
                {
                    poms.dependencies(other)
                            .visitDependencyClosure(scopes,
                                    includeOptionalDependencies, this);
                }
            }
            postFilter();
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
}
