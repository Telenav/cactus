package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.util.preconditions.Exceptions;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class DependencySet
{

    private final Map<MavenId, Dependency> byId = new HashMap<>();
    private final List<Dependency> depManagement = new ArrayList<>();
    private final List<Dependency> deps = new ArrayList<>();
    private final Set<MavenId> exclusions = new HashSet<>();
    private final Pom owner;
    private final PomResolver resolver;
    private final PropertyResolver props;
    private final Map<Pom, DependencySet> sets;
    private final Pom parent;

    DependencySet(Pom pom, Poms poms)
            throws Exception
    {

        this(pom, poms, poms.or(PomResolver.local()).memoizing());
    }

    DependencySet(Pom pom, Poms poms, PomResolver pomRes)
            throws Exception
    {
        this(pom, pomRes, new ParentsPropertyResolver(pom, pomRes).memoizing());
    }

    DependencySet(Pom pom, PomResolver resolver, PropertyResolver propResolver)
            throws Exception
    {
        this(pom, resolver, propResolver, new HashMap<>());
    }

    DependencySet(Pom pom, PomResolver resolver, PropertyResolver propResolver,
            Map<Pom, DependencySet> sets)
            throws Exception
    {
        this.owner = pom;
        this.resolver = resolver;
        this.sets = sets;
        this.parent = pom.parent().flatMapThrowing(par ->
        {
            return resolver.get(par);
        }).orElse(null);
        this.props = propResolver.or(PropertyResolver.coords(pom.coords, parent == null ? null : parent.coords));

        System.out.println("PROPS IS " + this.props);
        pom.toPomFile().visitDependencies(true, d ->
        {
            MavenId id = d.coords.toMavenId();
            byId.put(id, d);
            exclusions.addAll(d.exclusions);
        });
        pom.toPomFile().visitDependencies(false, d ->
        {
            MavenId id = d.coords.toMavenId();
            Dependency full = byId.get(id);
            if (full == null)
            {
                exclusions.addAll(d.exclusions);
                Dependency dep = d.resolve(props, resolver);
                deps.add(dep);
                byId.put(id, dep);
            }
            else
            {
                Dependency dep = d.resolve(props, resolver);
                full.merge(dep).ifPresentOrElse(merged ->
                {
                    exclusions.addAll(merged.exclusions);
                }, () -> deps.add(dep));
            }
        });
    }

    DependencySet deps(Pom pom)
    {
        if (pom.equals(this.owner))
        {
            return this;
        }
        if (!sets.containsKey(pom))
        {
            try
            {
                DependencySet result = new DependencySet(pom, resolver, props,
                        sets);
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

    Dependency resolve(Dependency orig)
    {
        if (orig.isResolved())
        {
            return orig;
        }
        // XXX this may be wrong and resolve against a child
        orig = orig.resolve(props, resolver);
        Dependency mine = byId.get(orig.toMavenId());
        if (mine != null && mine.isResolved())
        {
            return orig.merge(mine).orElse(orig);
        }
        return orig;
    }

    public Set<Dependency> directDependencies(Set<DependencyScope> scope)
    {
        List<DependencySet> parents = new ArrayList<>();
        owner.visitParents(resolver, parent ->
        {
            parents.add(deps(parent));
        });
        Map<MavenId, Dependency> seen = new HashMap<>();
        Set<Dependency> result = new LinkedHashSet<>();
        for (Dependency dep : deps)
        {
            if (!scope.contains(dep.scope))
            {
                continue;
            }
            Dependency res = seen.compute(dep.toMavenId(), (mid, old) ->
            {
                if (old != null)
                {
                    return dep.merge(dep).orElse(old);
                }
                return dep;
            });
            result.add(res);
        }
        for (DependencySet par : parents)
        {
            coalesce(seen, result, par.directDependencies(scope));
        }

        for (DependencySet par : parents)
        {
            Set<Dependency> newResult = new HashSet<>();
            for (Dependency dep : result)
            {
                Dependency resolved = par.resolve(dep);
                if (resolved != dep)
                {
                    seen.put(resolved.toMavenId(), resolved);
                    newResult.add(resolved);
                }
                else
                {
                    newResult.add(dep);
                }
            }
            result = newResult;
        }
        return result;
    }

    public Set<Dependency> fullDependencies(Set<DependencyScope> scope)
    {
        Set<Dependency> result = new LinkedHashSet<>();
        Set<Dependency> direct = directDependencies(scope);
        Set<DependencyScope> trans = EnumSet.noneOf(DependencyScope.class);
        for (DependencyScope sc : scope)
        {
            trans.addAll(sc.transitivity());
        }

        for (Dependency dep : direct)
        {
            ThrowingOptional<Pom> depPom = resolver.get(dep);
            if (depPom.isPresent())
            {
                Pom dp = depPom.get();
                Set<Dependency> depDeps = new LinkedHashSet<>(deps(dp)
                        .fullDependencies(trans));
                for (Dependency dd : depDeps)
                {
                    if (!dep.excludes(dd))
                    {
                        result.add(dd);
                    }
                }
            }
            else
            {
                System.err.println("Could not resolve pom for " + dep.coords);
            }
        }
        return result;
    }

    private void coalesce(Map<MavenId, Dependency> seen, Set<Dependency> current,
            Set<Dependency> add)
    {
        for (Dependency dep : add)
        {
            Dependency res = seen.compute(dep.toMavenId(), (mid, old) ->
            {
                if (old != null)
                {
                    return dep.merge(dep).orElse(old);
                }
                return dep;
            });
            current.add(res);
        }
    }
}
