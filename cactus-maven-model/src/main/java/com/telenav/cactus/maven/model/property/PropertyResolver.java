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
package com.telenav.cactus.maven.model.property;

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public interface PropertyResolver extends Iterable<String>,
                                          Function<String, String>
{

    /**
     * Get a property resolver which can resolve properties defined in a pom or
     * any of its parent poms.
     *
     * @param pom A pom
     * @param poms A resolver for locating parent poms.
     * @return A property resolver
     */
    static PropertyResolver parents(Pom pom, PomResolver poms)
    {
        return new ParentsPropertyResolver(pom, poms);
    }

    /**
     * Determine if a string contains any Maven <code>${ }</code> delimited
     * property-references.
     *
     * @param what A string
     * @return true if the string does not look like a property reference
     */
    static boolean isResolved(String what)
    {
        if (what == null)
        {
            return true;
        }
        int firstPropertyReferenceOpenOffset = what.indexOf("${");
        int lastPropertyCloseOffset
                = firstPropertyReferenceOpenOffset < 0
                  ? -1
                  : what.lastIndexOf('}');
        return !(firstPropertyReferenceOpenOffset >= 0
                && lastPropertyCloseOffset > firstPropertyReferenceOpenOffset);
    }

    /**
     * Creates a PropertyResolver which can resolve properties which are defined
     * by maven coodinates in the pom or parent pom, and a few other things,
     * specifically:
     * <ul>
     * <li><code>project.basedir</code></li>
     * <li><code>parent.basedir / project.parent.basedir</code></li>
     * <li><code>parent.basedir</code> - (same as previous)</li>
     * <li><code>project.version</code></li>
     * <li><code>project.groupId</code></li>
     * <li><code>project.artifactId</code></li>
     * <li><code>project.parent.artifactId / parent.artifactId</code></li>
     * <li><code>project.parent.groupId / parent.groupId</code></li>
     * <li><code>project.parent.version / parent.version</code></li>
     * <li><code>java.version</code> - uses the system property of the running
     * vm</li>
     * <li><code>maven.version</code> - since this library has no way of knowing
     * it, or if it is even being run from inside a maven process, there is a
     * static setter for it on CoordinatesPropertyResolver.</li>
     * </ul>
     *
     * @param self The main pom - must not be null
     * @param parent The parent of that pom - may be null if there is none
     * @return A property resolver.
     */
    static PropertyResolver coords(Pom self,
            Pom parent)
    {
        return new CoordinatesPropertyResolver(self, parent);
    }

    /**
     * Combine this PropertyResolver with another which is used as a fallback if
     * this one does not return a result.
     *
     * @param other Another property resolver
     * @return a property resolver
     */
    default PropertyResolver or(PropertyResolver other)
    {
        if (other == this)
        {
            return this;
        }
        return new PropertyResolver()
        {
            @Override
            public String resolve(String what)
            {
                String result = PropertyResolver.this.resolve(what);
                if (result != null)
                {
                    what = result;
                }
                if (!isResolved(what))
                {
                    String ores = other.resolve(what);
                    if (ores != null)
                    {
                        return ores;
                    }
                }
                return result == null
                       ? what
                       : result;
            }

            @Override
            public Iterator<String> iterator()
            {
                Set<String> all = new TreeSet<>();
                for (String k : PropertyResolver.this)
                {
                    all.add(k);
                }
                for (String k : other)
                {
                    all.add(k);
                }
                return all.iterator();
            }

            @Override
            public String toString()
            {
                return "or(" + PropertyResolver.this + ", " + other + ")";
            }

        };
    }

    /**
     * Resolve a property
     *
     * @param what The string to resolve a property in
     * @return A new string if it can be resolved, otherwise null
     */
    String resolve(String what);

    /**
     * Delegates to <code>resolve(String)</code> in order to implement
     * {@link Function}.
     *
     * @param toResolve The string to resolve properties in
     * @return A resolved string or null
     */
    @Override
    default String apply(String toResolve)
    {
        return resolve(toResolve);
    }

    /**
     * Returns a wrapper around this PropertyResolver which caches resolved
     * properties, for cases where lookups involve heavy I/O.
     *
     * @return A property resolver
     */
    default PropertyResolver memoizing()
    {
        return new PropertyResolver()
        {
            private final Map<String, Optional<String>> cache = new HashMap<>();

            @Override
            public String resolve(String what)
            {
                return cache.computeIfAbsent(what, w -> Optional.ofNullable(
                        PropertyResolver.this.resolve(
                                what))).orElse(null);
            }

            @Override
            public Iterator<String> iterator()
            {
                return PropertyResolver.this.iterator();
            }

            @Override
            public String toString()
            {
                return "memo(" + PropertyResolver.this + ")";
            }

            @Override
            public PropertyResolver memoizing()
            {
                return this;
            }
        };
    }

    /**
     * Create wrapper around a lazily constructed PropertyResolver, for cases
     * where the properties may naver be needed, to avoid the expense of reading
     * files until there is demand.
     *
     * @param supp A supplier of the real property resolver
     * @return A property resolver
     */
    public static PropertyResolver lazy(Supplier<PropertyResolver> supp)
    {
        return new PropertyResolver()
        {
            private PropertyResolver resolver;

            private synchronized PropertyResolver load()
            {
                return resolver == null
                       ? resolver = supp.get()
                       : resolver;
            }

            @Override
            public String resolve(String what)
            {
                return load().resolve(what);
            }

            @Override
            public Iterator<String> iterator()
            {
                return load().iterator();
            }

            @Override
            public String toString()
            {
                if (resolver == null)
                {
                    return "Lazy<unloaded>";
                }
                return resolver.toString();
            }
        };
    }
}
