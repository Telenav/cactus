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
package com.telenav.cactus.maven.model.dependencies;

import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.Pom;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * The set of dependencies of one POM.
 *
 * @author Tim Boudreau
 */
public interface Dependencies
{
    /**
     * Get the dependency closure - all transitive dependencies for the given
     * options.
     *
     * @param includeOptional If true, include dependencies marked as optional
     * @param scopes The set of scopes you want dependencies for
     * @return A set of dependencies
     */
    Set<Dependency> dependencyClosure(boolean includeOptional,
            Set<DependencyScope> scopes);

    /**
     * Get the transitive closure of dependencies in the passed set of scopes.
     *
     * @param scopes A set of scopes
     * @return A set of dependencies
     */
    Set<Dependency> dependencyClosure(Set<DependencyScope> scopes);

    /**
     * Get the transitive closure of dependencies for the passed set of scopes.
     *
     * @param scope A set of scopes - if none are passed, returns dependencies
     * for all Maven scopes
     * @return A set of dependencies
     */
    Set<Dependency> dependencyClosure(DependencyScope... scope);

    /**
     * Get the transitive closure of dependencies for the passed set of scopes.
     *
     * @param includeOptional whether or not to include and traverse
     * dependencies marked as optional
     * @param scope A set of scopes - if none are passed, returns dependencies
     * for all Maven scopes
     * @return A set of dependencies
     */
    Set<Dependency> dependencyClosure(boolean includeOptional,
            DependencyScope... scope);

    //
    // API methods
    //
    /**
     * Get the set of direct dependencies in the given scopes.
     *
     * @param scope A set of scopes
     * @return A set of dependencies
     */
    Set<Dependency> directDependencies(Set<DependencyScope> scope);

    /**
     * Get the set of direct dependencies in the given scopes.
     *
     * @param includeOptional If true, include dependencies marked as optional
     * @param scope A set of scopes
     * @return A set of dependencies
     */
    Set<Dependency> directDependencies(boolean includeOptional,
            Set<DependencyScope> scope);

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
    boolean visitDependencyClosure(Set<DependencyScope> scopes,
            boolean includeOptional, BiPredicate<Pom, Dependency> into);
}
