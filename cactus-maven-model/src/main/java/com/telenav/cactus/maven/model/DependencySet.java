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
    /*
    Finding maven dependencies, given a scope or set of scopes:
    
     - Read the <dependencies> section of the POM.  Versions may be defined
       in a <dependencyManagement> section of this POM or a parent POM.  If
       that exists and provides versions for things in its own <dependencies> section,
       resolve those immediately.
      - Dependencies may initially have their group id or version defined as
        property references, or they may be undefined entirely, relying on a
        <dependencyManagement> definition somewhere to define the rest
     - Read any <dependencyManagement> section in this POM, cache the values
       and resolve any dependencies you find
     - Find any dependencies of scope "import" add any <dependencyManagement>
       sections there to what's here, and any dependencies.  Note that properties
       used in those declaration are resolved in the scope of the _imported_ pom, not
       this one - if the version of foo is ${x} and the imported pom defines x as 2
       and this pom defines x as 3, the version of foo is 2 not 3
     - Until you're out of parents
       - Resolve the parent pom
       - If it declares any direct dependencies, these apply to everything -
         add them to the set of direct dependencies
       - Collect the set of properties defined there, which may be used to
         resolve versions
     - Now you have enough information to resolve any implicit or property-defined
       values in your dependencies - iterate and resolve
    */
    private final Map<MavenId, Dependency> byId = new HashMap<>();
    private final List<Dependency> deps = new ArrayList<>();
    private final Pom owner;
    private final PomResolver resolver;
    private final PropertyResolver props;
    private final Map<Pom, DependencySet> sets;
    private final Pom parent;

    public DependencySet(Pom pom, Poms poms)
            throws Exception
    {
        this(pom, poms, poms.or(PomResolver.local()));
    }

    public DependencySet(Pom pom, Poms poms, Map<Pom, DependencySet> sets)
            throws Exception
    {
        this(pom, poms, poms.or(PomResolver.local()).memoizing(), sets);
    }

    public DependencySet(Pom pom, Poms poms, PomResolver pomRes,
            Map<Pom, DependencySet> sets)
            throws Exception
    {
        this(pom, pomRes, new ParentsPropertyResolver(pom, pomRes).memoizing(),
                sets);
    }

    public DependencySet(Pom pom, Poms poms, PomResolver pomRes)
            throws Exception
    {
        this(pom, pomRes, new ParentsPropertyResolver(pom, pomRes).memoizing());
    }

    public DependencySet(Pom pom, PomResolver resolver,
            PropertyResolver propResolver)
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
        this.props = propResolver.or(PropertyResolver.coords(pom.coords,
                parent == null
                ? null
                : parent.coords)).memoizing();

        pom.toPomFile().visitDependencies(true, d ->
        {
            d = d.resolve(this.props, resolver);
            MavenId id = d.coords.toMavenId();
            System.out.println("" + pom.coords + " depMan " + d);
            byId.put(id, d);
        });
        pom.toPomFile().visitDependencies(false, d ->
        {
            MavenId id = d.coords.toMavenId();
            Dependency full = byId.get(id);
            if (full == null)
            {
                Dependency dep = resolve(d);
                deps.add(dep);
                byId.put(id, dep);
            }
            else
            {
//                Dependency dep = d.resolve(props, resolver);
                Dependency dep = resolve(full);
                full.merge(dep).ifPresentOrElse(merged ->
                {
                    deps.add(merged);
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
                PropertyResolver res = new ParentsPropertyResolver(pom, resolver);
                DependencySet result = new DependencySet(pom, resolver, res,
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

    public Dependency resolve(Dependency orig)
    {
        if (orig.isResolved())
        {
            return orig;
        }
        // XXX this may be wrong and resolve against a child
//        orig = orig.resolve(props, resolver);
        Dependency mine = byId.get(orig.toMavenId());
        if (mine != null && !mine.isResolved())
        {
            mine = mine.resolve(props, resolver);
        }
        if (mine != null && mine.isResolved())
        {
            orig = orig.merge(mine).orElse(orig);
        }
        if (parent != null && !orig.isResolved())
        {
            orig = deps(parent).resolve(orig);
        }
        return orig;
    }

    public Set<Dependency> directDependencies(Set<DependencyScope> scope)
    {
        return directDependencies(scope, new HashSet<>());
    }

    Set<Dependency> directDependencies(Set<DependencyScope> scope,
            Set<Object> traversed)
    {
        List<DependencySet> parents = new ArrayList<>();
        owner.visitParents(resolver, parent ->
        {
            parents.add(deps(parent));
        });
        traversed.add(this);
        Map<MavenId, Dependency> seen = new HashMap<>();
        Set<Dependency> result = new LinkedHashSet<>();
        for (Dependency dep : deps)
        {
            if (!scope.contains(dep.scope))
            {
                continue;
            }
            if (!dep.isResolved()) {
                dep = resolve(dep);
            }
            Dependency dd = dep;
            Dependency res = seen.compute(dep.toMavenId(), (mid, old) ->
            {
                if (old != null)
                {
                    return old.merge(dd).orElse(old);
                }
                return dd;
            });
            result.add(res);
        }
        for (DependencySet par : parents)
        {
            if (traversed.contains(par))
            {
                continue;
            }
            traversed.add(par);
            coalesce(seen, result, par.directDependencies(scope, traversed));
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
        return fullDependencies(scope, new HashSet<>());
    }

    Set<Dependency> fullDependencies(Set<DependencyScope> scope,
            Set<Object> traversed)
    {
        Set<Dependency> direct = directDependencies(scope, traversed);
        traversed.add(this);
        Set<Dependency> result = new LinkedHashSet<>(direct);
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
                DependencySet set = deps(dp);
                if (traversed.contains(set))
                {
                    continue;
                }
                traversed.add(set);
                Set<Dependency> depDeps = new LinkedHashSet<>(set
                        .fullDependencies(trans, traversed));
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
                if (!dep.optional)
                {
                    System.err.println("Could not resolve pom for " + dep.coords
                            + " for " + owner.coords);
                }
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
