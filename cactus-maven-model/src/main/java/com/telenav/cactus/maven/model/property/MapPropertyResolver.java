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

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * A PropertyResolver over a map.
 *
 * @author Tim Boudreau
 */
public final class MapPropertyResolver extends AbstractPropertyResolver
{

    private final Map<String, String> map;

    public MapPropertyResolver(Map<String, String> map)
    {
        this.map = map;
    }

    @Override
    protected String valueFor(String k)
    {
        return map.get(k);
    }

    @Override
    public Iterator<String> iterator()
    {
        return map.keySet().iterator();
    }

    Set<String> keys()
    {
        return map.keySet();
    }

    @Override
    public String toString()
    {
        return "Map(" + map.keySet() + ")";
    }
}
