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
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
public interface PropertyResolver extends Iterable<String>
{

    static PropertyResolver parents(Pom pom, PomResolver poms)
    {
        return new ParentsPropertyResolver(pom, poms);
    }

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

    static PropertyResolver coords(Pom self,
            Pom parent)
    {
        return new CoordinatesPropertyResolver(self, parent);
    }

    default PropertyResolver or(PropertyResolver other)
    {
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

    String resolve(String what);

    default PropertyResolver memoizing()
    {
        if (true)
        {
            return this;
        }
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
