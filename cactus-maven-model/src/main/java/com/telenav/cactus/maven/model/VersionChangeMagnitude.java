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
package com.telenav.cactus.maven.model;

import java.util.Iterator;
import java.util.List;

import static java.lang.Integer.min;

/**
 * Semantic versioning three-decimal (plus optional suffix) description of which
 * decimal to increment or change.
 *
 * @author Tim Boudreau
 */
public enum VersionChangeMagnitude
{
    /**
     * Change the major version, e.g. 1.3.2 to 2.0.0.
     */
    MAJOR,
    /**
     * Change the minor version, e.g. 1.2.3 to 1.3.0.
     */
    MINOR,
    /**
     * Change the dot version, e.g. 2.1.13 to 2.1.14.
     */
    DOT,
    /**
     * Don't change the leading decimal portion of the version.
     */
    NONE;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    public boolean isNone()
    {
        return this == NONE;
    }

    /**
     * Returns DOT if this == NONE, otherwise this, so we get a magnitude that
     * will result in at least a minimal change.
     *
     * @return A magnitude which is not NONE
     */
    public VersionChangeMagnitude notNone()
    {
        return this == NONE
               ? DOT
               : this;
    }

    /**
     * Increment the decimal corresponding with this enum constant in the passed
     * list, and return a dewey-decimal representation of it. Decimals may be
     * added to have something to increment.
     *
     * @param list A list of numbers
     * @return A dewey decimal string
     */
    String increment(List<Long> list)
    {
        if (this == NONE || list.isEmpty())
        {
            return toDotted(list);
        }
        change(1, list);
        return toDotted(list);
    }

    String change(long by, List<Long> list)
    {
        if (this == NONE || list.isEmpty())
        {
            return toDotted(list);
        }
        int ix;
        if (ordinal() > list.size())
        {
            // Need to add an ordinal to bump the right spot
            int zeros = ordinal() - (list.size() - 1);
            for (int i = 0; i < zeros; i++)
            {
                list.add(0L);
            }
            list.add(by);
            ix = list.size() - 1;
        }
        else
        {
            // Don't add ordinals, just increment, so 2.0 becomes 2.1
            ix = min(ordinal(), list.size() - 1);
            list.set(ix, list.get(ix) + by);
        }
        for (int i = ix + 1; i < list.size(); i++)
        {
            list.set(i, 0L);
        }
        return toDotted(list);
    }

    String changeTo(long to, List<Long> list)
    {
        if (this == NONE || list.isEmpty())
        {
            return toDotted(list);
        }
        int ix = min(ordinal(), list.size() - 1);
        list.set(ix, to);
        return toDotted(list);
    }

    private static <T> List<T> trim(List<T> list)
    {
        if (list.size() > 3)
        {
            return list.subList(0, 3);
        }
        return list;
    }

    public static String toDotted(List<? extends Number> l)
    {
        StringBuilder sb = new StringBuilder();
        for (Iterator<?> it = trim(l).iterator(); it.hasNext();)
        {
            sb.append(it.next());
            if (it.hasNext())
            {
                sb.append('.');
            }
        }
        return sb.toString();
    }

    public static VersionChangeMagnitude between(PomVersion old, PomVersion nue)
    {
        List<Long> oldDecimals = old.decimals();
        List<Long> newDecimals = nue.decimals();
        if (oldDecimals.isEmpty() || newDecimals.isEmpty())
        {
            return NONE;
        }
        int firstChange = -1;
        for (int i = 0; i < min(oldDecimals.size(), newDecimals.size()); i++)
        {
            if (!oldDecimals.get(i).equals(newDecimals.get(i)))
            {
                firstChange = i;
                break;
            }
        }
        if (firstChange == -1)
        {
            if (newDecimals.size() > oldDecimals.size())
            {
                firstChange = min(DOT.ordinal(), oldDecimals.size());
            }
        }
        if (firstChange == -1)
        {
            return NONE;
        }
        return values()[firstChange];
    }
}
