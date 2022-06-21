package com.telenav.cactus.maven.model.dependencies;

import com.telenav.cactus.maven.model.resolver.PomResolver;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import com.telenav.cactus.maven.model.property.CoordinatesPropertyResolver;
import com.telenav.cactus.maven.model.property.ParentsPropertyResolver;
import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.MavenId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.util.ThreadLocalStack;
import com.telenav.cactus.maven.model.util.ThreadLocalValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static java.util.Collections.emptySet;
import static java.util.Collections.newSetFromMap;
import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableSet;

/**
 *
 * @author Tim Boudreau
 */
public class DependencySet implements Dependencies
{
    // For debugging, gives us the ability to log the provenance of a resolution failure
    private static final ThreadLocalStack<Pom> INIT_PATH
            = ThreadLocalStack.create();
    // Use a listener in a thread local for contextual logging
    private static final ThreadLocalValue<ResolutionListener> LISTENER
            = ThreadLocalValue.create(DefaultResolutionListener::new);


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
    private final Set<Dependency> dependencyManagement = new HashSet<>();
    private final List<Dependency> dependencies = new ArrayList<>();
    private final Pom owner;
    private final PomResolver resolver;
    private final Map<Pom, DependencySet> resolutionContext;
    private final Pom parent;
    private final PropertyResolver localProperties;
    private final CoordinatesPropertyResolver coordinatesResolver;
    private final PropertyResolver fromParentsResolver;

    private boolean initialized;

    public DependencySet(Pom pom, PomResolver poms)
            throws Exception
    {
        this(pom, poms.or(PomResolver.local()), new ConcurrentHashMap<>());
    }

    public DependencySet(Pom pom, PomResolver resolver,
            Map<Pom, DependencySet> resolutionContext)
    {
        this.owner = pom;
        this.resolver = resolver;
        this.resolutionContext = resolutionContext;
        this.parent = pom.parent().flatMapThrowing(par ->
        {
            return resolver.get(par);
        }).orElse(null);
        // We need this to resolve "project.version" and a few other things
        coordinatesResolver = new CoordinatesPropertyResolver(pom, parent);
        // Can resolve properties throughout the parent tree - we don't use
        // it directly here, but utilize its caching mechanism
        ParentsPropertyResolver _parentsResolver = new ParentsPropertyResolver(
                pom, resolver);
        fromParentsResolver = _parentsResolver.memoizing();
        // Avoid reading the <properties> section of POMs until something
        // really needs them - this makes a significant performance difference.
        localProperties = PropertyResolver.lazy(() ->
        {
            return _parentsResolver.resolverFor(pom);
        });
    }

    //
    // API methods
    //
    /**
     * Get the set of direct dependencies in the given scopes.
     *
     * @param scope A set of scopes
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> directDependencies(Set<DependencyScope> scope)
    {
        return directDependencies(true, scope);
    }

    /**
     * Get the set of direct dependencies in the given scopes.
     *
     * @param includeOptional If true, include dependencies marked as optional
     * @param scope A set of scopes
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> directDependencies(boolean includeOptional,
            Set<DependencyScope> scope)
    {
        if (scope.isEmpty())
        {
            log(true,
                    "Set of scopes passed to directDependencies() is empty for " + owner);
            return emptySet();
        }
        checkInit();
        Set<Dependency> result = new LinkedHashSet<>();
        for (Dependency d : this.dependencies)
        {
            if (!scope.contains(d.scope) || !includeOptional && d.isOptional())
            {
                continue;
            }
            result.add(d);
        }
        return result;
    }

    /**
     * Traverse the closure of all dependencies that match the
     * <code>scopes</code> and <code>includeOptional</code> arguments, until the
     * traversal is completed or the passed predicate has returned false,
     * indicating that it is done scanning or searching the dependency tree.
     * <p>
     * If you just want the set of direct and indirect dependencies, use one of
     * the methods that returns that - this is specifically for the case that
     * you want to either search for one thing, or attribute dependencies to
     * their origin.
     * </p>
     *
     * @param scopes The scopes the initial dependencies should be in
     * @param includeOptional If true, include dependencies marked optional
     * @param A predicate which receives this set's owner pom, and one
     * dependency which matches the query requirements - if this returns false,
     * the scan is aborted (so you can search the dependency closure and exit if
     * you find what you're looking for).
     * @return The last return value of the passed predicate (false if it
     * aborted visiting early)
     */
    @Override
    public boolean visitDependencyClosure(Set<DependencyScope> scopes,
            boolean includeOptional, BiPredicate<Pom, Dependency> into)
    {
        return visitDependencyClosure(scopes, includeOptional, Collections
                .emptySet(),
                newSetFromMap(new IdentityHashMap<>()), into);
    }

    /**
     * Get the dependency closure - all transitive dependencies for the given
     * options.
     *
     * @param includeOptional If true, include dependencies marked as optional
     * @param scopes The set of scopes you want dependencies for
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> dependencyClosure(boolean includeOptional,
            Set<DependencyScope> scopes)
    {
        return DependencySet.this.dependencyClosure(scopes, includeOptional,
                emptySet(),
                new HashSet<>());
    }

    /**
     * Get the transitive closure of dependencies in the passed set of scopes.
     *
     * @param scopes A set of scopes
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> dependencyClosure(Set<DependencyScope> scopes)
    {
        return DependencySet.this.dependencyClosure(scopes, true, emptySet(),
                new HashSet<>());
    }

    /**
     * Get the transitive closure of dependencies for the passed set of scopes.
     *
     * @param scope A set of scopes - if none are passed, returns dependencies
     * for all Maven scopes
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> dependencyClosure(
            DependencyScope... scope)
    {
        return DependencySet.this.dependencyClosure(true, scope);
    }

    /**
     * Get the transitive closure of dependencies for the passed set of scopes.
     *
     * @param includeOptional whether or not to include and traverse
     * dependencies marked as optional
     * @param scope A set of scopes - if none are passed, returns dependencies
     * for all Maven scopes
     * @return A set of dependencies
     */
    @Override
    public Set<Dependency> dependencyClosure(boolean includeOptional,
            DependencyScope... scope)
    {
        if (scope.length == 0)
        {
            scope = DependencyScope.values();
        }
        return DependencySet.this
                .dependencyClosure(DependencyScope.setOf(scope),
                        true, emptySet(), newSetFromMap(new IdentityHashMap<>()));
    }

    //
    // Implementation methods
    //
    private Set<Dependency> dependencyManagementEntries()
    {
        checkInit();
        return unmodifiableSet(dependencyManagement);
    }

    private PropertyResolver propertyResolver()
    {
        return localProperties;
    }

    private Dependency depManagementEntry(Dependency of)
    {
        checkInit();
        for (Dependency dep : dependencyManagement)
        {
            if (dep.isCompletionOf(of))
            {
                return dep;
            }
        }
        return null;
    }

    /**
     * Initializes the direct depenencies set if it has not already been.
     */
    private synchronized void checkInit()
    {
        if (!initialized)
        {
            initialized = true;
            resolutionContext.putIfAbsent(owner, this);
            init();
        }
    }

    /**
     * Perform initialization.
     */
    private void init()
    {
        try
        {
            PomFile pf = PomFile.of(owner);
            // inContextRun() ensures that the Document instance is held in
            // a ThreadLocal while we are using it, so we don't repeatedly
            // parse the pom.xml each time we want to read a value from it
            pf.inContextRun(() ->
            {
                initialize(pf);
            });
        }
        catch (Exception ex)
        {
            Exceptions.chuck(ex);
        }
    }

    /**
     * Walks the POM and its parents and finds all direct dependencies,
     * resolving any properties used in them, and applying any
     * dependencyManagement entries to make the dependencies complete.
     *
     * @param file The owner as a PomFile which can parse the XML of a pom.xml
     */
    private void initialize(PomFile file)
    {
        // Called the first time this instance is used for something that
        // requires dependency resolution.
        //
        // We populate and fully resolve direct dependencies here.

        // We can already be added because we are collecting full dependencies,
        // so don't show up twice in the path
        INIT_PATH.pushingThrowing(owner, () ->
        {
            log(false, "init " + owner.coords);
            // Maintain declaration order so we match classpath order:
            Set<Dependency> dependenciesLocal = new LinkedHashSet<>();
            // Resolves properties against our own pom's properties, and
            // some built-in attributes like project.groupId
            PropertyResolver localResolver = coordinatesResolver.or(
                    localProperties).memoizing();
            // Parent hierarchy - don't populate unless we need it
            List<Pom> parentsList = unmodifiableList(owner.parents(resolver));

            // Fetch the raw entries from the pom file, which may contain
            // unspecified versions to be resolved from parents or properties
            List<Dependency> rawDependencyManagementEntries
                    = file.dependencies(true);
            List<Dependency> rawDependencies = file.dependencies(false);

            resolveDependencyManagementEntries(rawDependencyManagementEntries,
                    localResolver, parentsList);

            // See
            // https://developer.jboss.org/docs/DOC-15811#:~:text=Maven%20includes%20a%20dependency%20scope,from%20a%20remote%20POM%20file.&text=This%20page%20is%20meant%20to,caveats%20of%20the%20import%20scope.
            // Import scope's exclusions override local exclusions, including wiping
            // them out.  So keep a map of these.
            Map<MavenId, Dependency> managementEntriesFromImports = new HashMap<>();
            collectDependencyManagementItemsFromImportScopeDependencies(
                    rawDependencies,
                    localResolver, parentsList, managementEntriesFromImports);

            // Now bring in any dependencies declared as straight dependencies
            // in parent poms - these become direct dependencies of all children.
            //
            // We need to track these by ID, because the child project can replace
            // the dependency.
            //
            // And we need them in source order because classpath order is a thing.
            Map<MavenId, Dependency> inheritedById = new LinkedHashMap<>();
            collectDirectDependenciesDefinedInParents(parentsList,
                    inheritedById);

            // Now, finally, we can resolve our direct dependencies
            for (Dependency dep : rawDependencies)
            {
                // Save the original entry for diagnostic error messages if
                // we fail to resolve it fully
                Dependency orig = dep;
                // If we are clobbering a direct dependency declared in a parent,
                // remove it from the set we're going to append to the end of
                // our local direct dependencies list.
                Dependency existing = inheritedById.remove(dep.coords
                        .toMavenId());
                // Update the dependency replacing any ${project.groupId},
                // ${project.version} or local properties
                if (!dep.isResolved())
                {
                    dep = dep.resolve(localResolver);
                }

                Dependency checkpoint = dep;
                Dependency managementEntry = this.depManagementEntry(dep);
                // We need this even if resolved, to augment the dependency
                // with anything defined there (or clobber it, because maven
                // does that)
                if (managementEntry != null)
                {
                    dep = applyDependencyManagementPropertiesToDependency(
                            managementEntry, dep);
                }

                if (!dep.isResolved())
                {
                    dep = resolveViaParents(dep, localResolver, parentsList);
                }
                // Fudge it if we have a resolved dependency from a parent and
                // the child one is unresolvable
                if (existing != null && existing.isResolved() && !dep
                        .isResolved())
                {
                    dep = existing.withCombinedExclusions(dep.exclusions());
                }

                if (checkpoint != dep && checkpoint.isResolved() && !dep
                        .isResolved())
                {
                    throw new Error(
                            "Resolution info lost between " + checkpoint + " and " + dep);
                }

                if (!dep.isResolved())
                {
                    dep = dep.resolve(this.fromParentsResolver, resolver);
                }

                if (!dep.isResolved())
                {
                    // We may have not had sufficent properties resolved before,
                    // so give it another try
                    managementEntry = this.depManagementEntry(dep);
                    // We need this even if resolved, to augment the dependency
                    // with anything defined there (or clobber it, because maven
                    // does that)
                    if (managementEntry != null)
                    {
                        dep = applyDependencyManagementPropertiesToDependency(
                                managementEntry, dep);
                    }
                }

                if (!dep.isResolved())
                {
                    onResolutionFailure(dep, "dependencies", orig);
                }

                Dependency imported = managementEntriesFromImports.get(dep
                        .toMavenId());
                // XXX if it DOESN'T define exclusions, can you still have your own?
                if (imported != null && !imported.exclusions().isEmpty())
                {
                    if (!dep.exclusions().isEmpty())
                    {
                        log(true, "Import exclusions in " + imported
                                + " will clobber exclusions in " + dep + " for "
                                + owner.coords);
                    }
                    dep = dep.withExclusions(imported.exclusions());
                }
                dependenciesLocal.add(dep);
            }
            // Add any imported dependencies that were not overridden to
            // the end of the colllection
            dependenciesLocal.addAll(inheritedById.values());
            // And add them, in classpath order, to the instance's list field
            this.dependencies.addAll(dependenciesLocal);
            log(false, "Completed init of " + owner.coords + " with "
                    + dependenciesLocal.size() + " dependencies and "
                    + dependencyManagement.size() + " depManagement entries");
        });

    }

    private void collectDirectDependenciesDefinedInParents(List<Pom> parentsList,
            Map<MavenId, Dependency> inheritedById)
    {
        for (Pom oneParent : parentsList)
        {
            DependencySet parentDeps = dependenciesOf(oneParent);
            Set<Dependency> direct = parentDeps.directDependencies(
                    DependencyScope.all());
            for (Dependency dep : direct)
            {
                MavenId id = dep.coords.toMavenId();
                // A child can also be replacing the dependency.
                if (!inheritedById.containsKey(id))
                {
                    inheritedById.put(id, dep);
                }
            }
        }
    }

    private void collectDependencyManagementItemsFromImportScopeDependencies(
            List<Dependency> rawDependencies, PropertyResolver localResolver,
            List<Pom> parentsList,
            Map<MavenId, Dependency> managementEntriesFromImports)
    {
        // Now pull in any import scope dependencies and add their entries
        // to ours, as they determine what else is resolvable
        for (Dependency dep : rawDependencies)
        {
            Dependency raw = dep;
            if (DependencyScope.Import == dep.scope)
            {
                if (!dep.isResolved())
                {
                    dep = resolveViaParents(dep, localResolver,
                            parentsList);
                }
                if (!dep.isResolved())
                {
                    onResolutionFailure(dep, "importDependencies", raw);
                }
                Dependency d = dep;
                // If this is unresolved, we may match
                resolver.get(dep).ifPresentOrElse(pom ->
                {
                    DependencySet pomSet = dependenciesOf(pom);
                    Set<Dependency> entries = pomSet
                            .dependencyManagementEntries();
                    dependencyManagement.addAll(entries);
                    for (Dependency importedDep : dependencyManagement)
                    {
                        managementEntriesFromImports.put(
                                importedDep.toMavenId(), importedDep);
                    }
                    dependencyManagement.addAll(pomSet
                            .dependencyManagementEntries());
                }, () ->
                {
                    onPomLookupFailure(d, "importDependencies", raw);
                });
            }
        }
    }

    private void resolveDependencyManagementEntries(
            List<Dependency> rawDependencyManagementEntries,
            PropertyResolver localResolver, List<Pom> parentsList)
    {
        // First flesh out our DependencyManagement entries
        for (Dependency dep : rawDependencyManagementEntries)
        {
            Dependency raw = dep;
            if (!dep.isResolved())
            {
                dep = dep.resolve(localResolver, resolver);
            }
            if (!dep.isResolved())
            {
                dep = resolveViaParents(dep, localResolver, parentsList);
            }
            if (!dep.isResolved())
            {
                onResolutionFailure(dep, "importDependencies", raw);
            }
            dependencyManagement.add(dep);
        }
    }

    private Dependency applyDependencyManagementPropertiesToDependency(
            Dependency managementEntry, Dependency dep)
    {
        // If we have a version, and there is none in the raw dependency, apply it
        if (!managementEntry.isPlaceholderVersion() && dep
                .isPlaceholderVersion())
        {
            dep = dep.withVersion(managementEntry.coords.version);
        }
        // This is wrong, but it is what maven does - if a dependency
        // management entry specifies a type or scope, it is nearly
        // impossible to use it
        if (dep.isImplictScope() && !managementEntry
                .isImplictScope())
        {
            dep = dep.withScope(managementEntry.scope);
        }
        // Same for type - it's why a dependencyManagement entry with a scope
        // like test or provided will hijack all dependencies on that thing to
        // those scopes or types
        if (dep.isImplicitType() && !managementEntry
                .isImplicitType())
        {
            dep = dep.withType(managementEntry.type);
        }
        // Unclear if dependency management exclusions combine with or clobber
        // explicit ones.  Combine for now.
        dep = dep.withCombinedExclusions(managementEntry
                .exclusions());
        return dep;
    }

    private Dependency resolveViaParents(Dependency dep,
            PropertyResolver localResolver, List<Pom> traverse)
    {
        Dependency orig = dep;

        if (!dep.isResolved())
        {
            // First resolve against our own properties
            dep = dep.resolve(localResolver);
            if (!dep.isResolved())
            {
                dep = resolveDependencyUsingParentPoms(traverse, dep);
                // If it's not resolved here, it can't be resolved
            }
            dep = dep.withCombinedExclusions(orig.exclusions());
        }
        if (!orig.isImplicitType())
        {
            dep = dep.withType(orig.type);
        }
        if (!orig.isImplictScope())
        {
            dep = dep.withScope(orig.scope);
        }
        return dep;
    }

    private Dependency resolveDependencyUsingParentPoms(List<Pom> parents,
            Dependency dep)
    {
        // Ensure we aren't already done.
        if (dep.isResolved())
        {
            return dep;
        }
        // Iterate the parents
        for (Pom pom : parents)
        {
            // Get the parents dependency set - this is cheap if we
            // don't force it to init.
            DependencySet set = dependenciesOf(pom);
            if (!dep.isResolved())
            {
                // See if we can get to complete resolution using that
                // parents properties
                dep = dep.resolve(set.propertyResolver());
            }
            if (!dep.isResolved())
            {
                // XXX - it may be that we need to do this even IF the
                // dep is resolved - it appears scopes and types and exclusions
                // in a parent can clobber ours
                Dependency managementEntry = set.depManagementEntry(dep);
                if (managementEntry != null)
                {
                    dep = applyDependencyManagementPropertiesToDependency(
                            managementEntry, dep);
                }
            }
            if (dep.isResolved())
            {
                break;
            }
        }
        return dep;
    }

    /**
     * Create and cache a DependencySet, or return an existing one stored in the
     * context. This ensures that we don't reread repeatedly used dependencies
     * and resolve all the dependencies as many times as they occur.
     *
     * @param pom A pom instance
     * @return A dependency set
     */
    private DependencySet dependenciesOf(Pom pom)
    {
        // The initiating pom will not necessarily be there
        if (pom.equals(this.owner))
        {
            return this;
        }
        return resolutionContext.computeIfAbsent(pom, p
                -> new DependencySet(pom, resolver,
                        resolutionContext)
        );
    }

    private Set<Dependency> dependencyClosure(Set<DependencyScope> scopes,
            boolean includeOptional, Set<MavenId> exclude,
            Set<Object> traversed)
    {
        // Put our direct dependencies first (should this really be depth first
        // or breadth first?  Need to examine some verbose maven logging 
        // classpaths
        Set<Dependency> result = new LinkedHashSet<>(256);
        collectDependencyClosure(scopes, includeOptional, exclude, traversed,
                result);
        return result;
    }

    /**
     * Does the heavy lifting of recursively traversing dependencies.
     *
     * @param scopes The set of scopes to collect for
     * @param includeOptional Whether or dependencies marked optional should be
     * returned
     * @param exclude A set of maven ids to exclude
     * @param traversed The set of items seen, so we do not visit a dependency
     * twice if, say, A depends on B + C and B and C both depend on D - this
     * both prevents unnecessary work and endless loops
     * @param into The collection to collect into
     */
    private void collectDependencyClosure(Set<DependencyScope> scopes,
            boolean includeOptional, Set<MavenId> exclude,
            Set<Object> traversed, Set<? super Dependency> into)
    {
        visitDependencyClosure(scopes, includeOptional, exclude, traversed,
                (p, dep) ->
        {
            into.add(dep);
            return true;
        });
    }

    /**
     * Does the heavy lifting of recursively traversing dependencies.
     *
     * @param scopes The set of scopes to collect for
     * @param includeOptional Whether or dependencies marked optional should be
     * returned
     * @param exclude A set of maven ids to exclude
     * @param traversed The set of items seen, so we do not visit a dependency
     * twice if, say, A depends on B + C and B and C both depend on D - this
     * both prevents unnecessary work and endless loops
     * @param A predicate which receives this set's owner pom, and one
     * dependency which matches the query requirements - if this returns false,
     * the scan is aborted (so you can search the dependency closure and exit if
     * you find what you're looking for).
     */
    private boolean visitDependencyClosure(Set<DependencyScope> scopes,
            boolean includeOptional, Set<MavenId> exclude,
            Set<Object> traversed, BiPredicate<Pom, Dependency> into)
    {
        // Add our owner to the thread-local path, for logging
        return INIT_PATH.pushing(owner, () ->
        {
            log(false, "Collect dep closure: " + owner.coords + " for " + scopes
                    + (!includeOptional
                       ? " excluding optional dependencies"
                       : ""));
            // Preinit our direct dependencies
            checkInit();
            // And collect them, filtering for the requested scope
            Set<Dependency> direct = directDependencies(includeOptional, scopes);
            // Make sure we don't reenter
            traversed.add(this);
            // Get the set of transitive scopes we'll need to fetch from each child
            // dependency
            Set<DependencyScope> transitiveScopes = DependencyScope
                    .transitivityOf(
                            scopes);

            // If this is a scope like "import" then direct dependencies are
            // it - it is non-transitive. So just return them and be done with it.
            if (transitiveScopes.isEmpty())
            {
                for (Dependency dep : direct)
                {
                    if (!into.test(owner, dep))
                    {
                        return false;
                    }
                }
                return true;
            }

            // Now go through our direct dependencies and pull in all of their dependencies
            for (Dependency dep : direct)
            {
                // See if this is one we should skip
                if ((dep.isOptional() && !includeOptional) || !scopes.contains(
                        dep.scope))
                {
                    continue;
                }
                if (!into.test(owner, dep))
                {
                    return false;
                }
                // Generate the superset of the exclusions we were passed any any
                // applied by this dependency
                Set<MavenId> combinedExclusions = combineExcludes(exclude, dep);

                ThrowingOptional<Pom> depsPom = resolver.get(dep);
                if (depsPom.isPresent())
                {
                    Pom dp = depsPom.get();
                    // Get the dependencies of this depenedency
                    DependencySet set = dependenciesOf(dp);
                    try ( var _ignored = INIT_PATH.push(dp))
                    {
                        // Means the POMs are badly broken, but we don't want to endlessly
                        // loop here.
                        if (set == this)
                        {
                            log(true, "Encountered self-dependency analyzing "
                                    + dep + " for " + owner);
                            continue;
                        }
                        // If we've already seen it, then its closure is already
                        // incorporated into our result, so don't do it again
                        if (traversed.contains(set))
                        {
                            continue;
                        }
                        // Mark this dependency as traversed so we don't traverse it again
                        traversed.add(set);

                        // Recurse, getting the full dependency set of this dependency
                        boolean keepGoing = set.visitDependencyClosure(
                                transitiveScopes,
                                includeOptional,
                                combinedExclusions,
                                traversed, into);
                        if (!keepGoing)
                        {
                            return false;
                        }
                    }
                }
                else
                {
                    // Log the faiilure, or something
                    onPomLookupFailure(dep, "fullDeps", dep);
                }
            }
            return true;
        });
    }

    /**
     * Efficiently combine the Set of ids to exclude from one dependency, and a
     * set that was already in use.
     *
     * @param existing An existing set of excludes
     * @param dep A dependency that may have its own excludes
     * @return A set that combines them
     */
    private static Set<MavenId> combineExcludes(Set<MavenId> existing,
            Dependency dep)
    {
        // Avoid copying the set if we don't need to
        Set<MavenId> depExcludes = dep.exclusions();
        if (depExcludes.isEmpty())
        {
            return existing;
        }
        else
            if (existing.isEmpty())
            {
                return depExcludes;
            }
            else
            {
                Set<MavenId> result = new HashSet<>(depExcludes);
                result.addAll(existing);
                return result;
            }
    }

    // Logging / error reporting from here down:
    private void log(boolean important, String msg)
    {
        LISTENER.usingValue(lis ->
        {
            lis.log(important, new ResolutionPath(), msg);
        });
    }

    private void onResolutionFailure(Dependency of, String phase,
            Dependency raw)
    {
        LISTENER.usingValue(lis ->
        {
            lis.onResolutionFailure(new ResolutionPath(), of, phase,
                    raw);
        });
    }

    private void onPomLookupFailure(Dependency of, String phase,
            Dependency raw)
    {
        LISTENER.usingValue(lis ->
        {
            lis
                    .onPomLookupFailure(new ResolutionPath(), of, phase,
                            raw);
        });
    }

    /**
     * Used in logging to give access to the recursive path of things being
     * resolved.
     */
    public static final class ResolutionPath implements Iterable<Pom>
    {
        private final List<Pom> poms;

        ResolutionPath()
        {
            this.poms = unmodifiableList(INIT_PATH.copy());
        }

        @Override
        public String toString()
        {
            return appendInitPath(new StringBuilder()).toString();
        }

        public StringBuilder appendInitPath(StringBuilder into)
        {
            if (poms.isEmpty())
            {
                into.append("-empty-");
                return into;
            }
            for (Iterator<Pom> it = poms.iterator(); it.hasNext();)
            {
                into.append(it.next().coords);
                if (it.hasNext())
                {
                    into.append(" <-- ");
                }
            }
            return into;
        }

        @Override
        public Iterator<Pom> iterator()
        {
            return poms.iterator();
        }
    }

    // Methods to allow setting up a listener on the current thread before
    // doing some work
    /**
     * Sets a listener in the ThreadLocal which will be used to report errors,
     * before doing some work.
     *
     * @param listener The listener
     * @param run The work
     */
    public static void withListener(ResolutionListener listener, Runnable run)
    {
        LISTENER.withValue(listener, run);
    }

    /**
     * Sets a listener in the ThreadLocal which will be used to report errors,
     * before doing some work.
     *
     * @param listener The listener
     * @param run The work
     */
    public static void withListenerThrowing(ResolutionListener listener,
            ThrowingRunnable run)
    {
        LISTENER.withValueThrowing(listener, run);
    }

    /**
     * Sets a listener in the ThreadLocal which will be used to report errors,
     * before doing some work.
     *
     * @param <T> The return type
     * @param listener The listener
     * @param supp The work
     * @return The result of the supplier
     */
    public static <T> T withListener(ResolutionListener listener,
            Supplier<T> supp)
    {
        return LISTENER.withValue(listener, supp);
    }

    /**
     * Sets a listener in the ThreadLocal which will be used to report errors,
     * before doing some work.
     *
     * @param <T> The return type
     * @param listener The listener
     * @param supp The work
     * @return The result of the supplier
     */
    public static <T> T withListener(ResolutionListener listener,
            ThrowingSupplier<T> run)
    {
        return LISTENER.withValueThrowing(listener, run);
    }

    /**
     * Allows for listening for log messages and errors in the resolution
     * process. Use one the the static methods to set the listener while doing
     * some work with a DependencySet.
     */
    public interface ResolutionListener
    {
        /**
         * Log a message.
         *
         * @param important Whether or not the message represents a potential
         * problem with the results
         * @param path The recursive path through dependencies at which the
         * message logging was invoked
         * @param what The message to log
         */
        void log(boolean important, ResolutionPath path, String what);

        /**
         * Called when a dependency could not be fully resolved (version
         * information in dependencyManagement or a property it references could
         * not be resolved).
         *
         * @param path The recursive path through dependencies at which the
         * message logging was invoked
         * @param of The dependency as it appeared when the lookup was performed
         * @param phase The task in progress when the failure was encountered
         * @param raw The raw dependency as declared in the POM, before applying
         * and properties or dependencyManagement values
         */
        void onResolutionFailure(ResolutionPath path, Dependency of,
                String phase,
                Dependency raw);

        /**
         * Called when a POM file could not be found for a resolved dependency.
         *
         * @param path The recursive path through dependencies at which the
         * message logging was invoked
         * @param of The dependency as it appeared when the lookup was performed
         * @param phase The task in progress when the failure was encountered
         * @param raw The raw dependency as declared in the POM, before applying
         * and properties or dependencyManagement values
         */
        void onPomLookupFailure(ResolutionPath path, Dependency of, String phase,
                Dependency raw);
    }

    /**
     * Default listener that just logs to stderr.
     */
    private static final class DefaultResolutionListener implements
            ResolutionListener
    {
        @Override
        public void log(boolean important, ResolutionPath path, String what)
        {
            if (important)
            {
                System.err.println((important
                                    ? "!!"
                                    : "") + path + ": " + what);
            }
        }

        @Override
        public void onResolutionFailure(ResolutionPath path, Dependency of,
                String phase, Dependency raw)
        {
            StringBuilder sb = new StringBuilder().append(path).append(' ');
            sb.append(phase).append(": Failure resolving ").append(of).append(
                    ' ');
            if (raw != of && !raw.equals(of))
            {
                sb.append(" Raw dependency: ").append(raw);
            }
            System.err.println(sb);
        }

        @Override
        public void onPomLookupFailure(ResolutionPath path, Dependency of,
                String phase, Dependency raw)
        {
            StringBuilder sb = new StringBuilder().append(path).append(' ');
            sb.append(phase).append(": Could not look up POM file for ").append(
                    of)
                    .append(' ');
            if (raw != of && !raw.equals(of))
            {
                sb.append(" Raw dependency: ").append(raw);
            }
            System.err.println(sb);
        }
    }
}
