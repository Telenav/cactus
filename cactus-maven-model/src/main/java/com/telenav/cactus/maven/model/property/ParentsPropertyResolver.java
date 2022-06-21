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

import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.resolver.PomResolver;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A property resolver which can resolve properties in a pom's properties or
 * those of its parent poms.
 *
 * @author Tim Boudreau
 */
public final class ParentsPropertyResolver extends AbstractPropertyResolver
{

    private final List<Pom> allPoms;
    private final Map<Pom, MapPropertyResolver> resolverForPom = new HashMap<>();

    public ParentsPropertyResolver(Pom pom, PomResolver pomResolver)
    {
        allPoms = pom.hierarchyDescending(pomResolver);
    }

    @Override
    public synchronized String resolve(String what)
    {
        return super.resolve(what);
    }

    @Override
    public String toString()
    {
        return ("parents(" + allPoms + ")");
    }

    @Override
    public Iterator<String> iterator()
    {
        Set<String> result = new TreeSet<>();
        for (Pom p : allPoms)
        {
            result.addAll(resolverFor(p).keys());
        }
        return result.iterator();
    }

    @Override
    protected String valueFor(String k)
    {
        for (Pom pom : allPoms)
        {
            String x = resolverFor(pom).valueFor(k);
            if (x != null && !k.equals(x))
            {
                return x;
            }
        }
        return null;
    }

    public MapPropertyResolver resolverFor(Pom pom)
    {
        return resolverForPom.computeIfAbsent(pom, p ->
        {
            try
            {
                return new MapPropertyResolver(p.properties());
            }
            catch (Exception ex)
            {
                return Exceptions.chuck(ex);
            }
        });
    }
}
