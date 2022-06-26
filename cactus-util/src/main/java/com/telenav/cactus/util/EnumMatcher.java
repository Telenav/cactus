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
package com.telenav.cactus.util;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

/**
 * Case insensitive matching of enums which allows for underscores to be
 * replaced with hyphens.
 *
 * @author Tim Boudreau
 */
public final class EnumMatcher<E extends Enum<E>> implements Iterable<E>
{

    private final E[] values;

    EnumMatcher(Class<E> type)
    {
        this.values = notNull("type", type).getEnumConstants();
    }

    public static <E extends Enum<E>> EnumMatcher<E> enumMatcher(Class<E> type)
    {
        return new EnumMatcher<>(type);
    }

    public E match(String what, E failover)
    {
        notNull("failover", failover);
        if (what == null)
        {
            return failover;
        }
        return match(what.trim()).orElse(failover);
    }

    public Optional<E> match(String what)
    {
        if (what == null || what.isEmpty())
        {
            return Optional.empty();
        }
        what = what.trim();
        for (E e : this)
        {
            if (isMatch(e, what))
            {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    private boolean isMatch(E toTest, String with)
    {
        return with.equals(toTest.name())
                || with.equals(lowerCase(toTest))
                || with.equals(hyphenated(toTest))
                || with.equals(hyphenatedLower(toTest));
    }

    private static String lowerCase(Enum<?> e)
    {
        return e.name().toLowerCase();
    }

    private static String hyphenated(Enum<?> e)
    {
        return e.name().replace('_', '-');
    }

    private static String hyphenatedLower(Enum<?> e)
    {
        return lowerCase(e).replace('_', '-');
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (E item : values)
        {
            if (sb.length() > 0)
            {
                sb.append(", ");
            }
            sb.append(item.name().toLowerCase().replace('_', '-'));
        }
        return sb.toString();
    }

    @Override
    public Iterator<E> iterator()
    {
        return Arrays.asList(values).iterator();
    }
}
