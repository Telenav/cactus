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
package com.telenav.cactus.maven.versions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.lang.Integer.max;

/**
 *
 * @author timb
 */
public enum PomRole
{
    PARENT,
    BILL_OF_MATERIALS,
    CONFIG,
    CONFIG_ROOT,
    JAVA,
    /**
     * A UFO.
     */
    UNKNOWN;

    boolean isPomProject()
    {
        return this != JAVA;
    }

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    public static <T extends Comparable<? super T>> void visitMapEntriesSorted(
            Map<T, Set<PomRole>> map, BiConsumer<T, Set<PomRole>> roleConsumer)
    {
        List<Map.Entry<T, Set<PomRole>>> entries = new ArrayList<>(
                map.entrySet());
        entries.sort((a, b) ->
        {
            int result = Integer.compare(maxOrdinal(a.getValue()),
                    maxOrdinal(b.getValue()));
            if (result == 0)
            {
                result = a.getKey().compareTo(b.getKey());
            }
            return result;
        });
        entries.forEach(entry ->
        {
            roleConsumer.accept(entry.getKey(), entry.getValue());
        });
    }

    private static <E extends Enum<E>> int maxOrdinal(Set<E> set)
    {
        int result = -1;
        for (E e : set)
        {
            result = max(result, e.ordinal());
        }
        return result;
    }

}
