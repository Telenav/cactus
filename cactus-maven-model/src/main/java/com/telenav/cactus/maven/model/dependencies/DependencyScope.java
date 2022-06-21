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
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableSet;

/**
 * The set of maven scopes defined in its docs.
 *
 * @author Tim Boudreau
 */
public enum DependencyScope
{
    Compile,
    Test,
    Provided,
    Runtime,
    Import;
    private static Set<DependencyScope> all;
    private Set<DependencyScope> trans;
    private Set<DependencyScope> asSet;

    @Override
    public String toString()
    {
        return name().toLowerCase();
    }

    public static Set<DependencyScope> transitivityOf(
            Collection<? extends DependencyScope> scopes)
    {
        if (scopes.size() == 1)
        {
            return scopes.iterator().next().transitivity();
        }
        Set<DependencyScope> result = EnumSet.noneOf(DependencyScope.class);
        for (DependencyScope scope : scopes)
        {
            result.addAll(scope.transitivity());
        }
        return result;
    }

    public static Set<DependencyScope> all()
    {
        if (all == null)
        {
            all = setOf(values());
        }
        return Collections.unmodifiableSet(all);
    }

    public Set<DependencyScope> asSet()
    {
        if (asSet == null)
        {
            asSet = EnumSet.of(this);
        }
        return Collections.unmodifiableSet(asSet);
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public static Set<DependencyScope> setOf(DependencyScope... scopes)
    {
        Set<DependencyScope> result = EnumSet.noneOf(DependencyScope.class);
        for (DependencyScope sc : scopes)
        {
            result.add(sc);
        }
        return result;
    }

    public Set<DependencyScope> transitivity()
    {
        if (trans != null)
        {
            return Collections.unmodifiableSet(trans);
        }
        switch (this)
        {
            // Pending:  Check these.
            case Compile:
                return trans = unmodifiableSet(setOf(Compile, Runtime));
            case Test:
                return trans = unmodifiableSet(setOf(Compile, Runtime, Provided));
            case Provided:
                return trans = unmodifiableSet(setOf(Compile, Runtime, Provided));
            case Import:
                return trans = emptySet();
            case Runtime:
                return trans = unmodifiableSet(setOf(Compile, Runtime));
            default:
                throw new AssertionError(this);
        }
    }

    public static DependencyScope of(String what)
    {
        if (what == null || what.isBlank())
        {
            return null;
        }
        switch (what)
        {
            case "compile":
                return Compile;
            case "test":
                return Test;
            case "provided":
                return Provided;
            case "runtime":
                return Runtime;
            case "import":
                return Import;
            default:
                return Compile;
        }
    }

    public DependencyScope coalesce(DependencyScope other)
    {
        if (other.ordinal() < ordinal())
        {
            return other;
        }
        else
        {
            return this;
        }
    }

    public boolean includes(Dependency dep)
    {
        return transitivity().contains(dep.scope);
    }
}
