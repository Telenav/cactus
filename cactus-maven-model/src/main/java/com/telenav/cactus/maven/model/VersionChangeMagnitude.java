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
    NONE;
    
    public boolean isNone() {
        return this == NONE;
    }

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
}
