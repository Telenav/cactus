package com.telenav.cactus.maven.util;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

/**
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
        if (what == null)
        {
            return failover;
        }
        return match(what).orElse(notNull("failover", failover));
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
                || with.equals(hyphenated(toTest));
    }

    private static String lowerCase(Enum<?> e)
    {
        return e.name().toLowerCase();
    }

    private static String hyphenated(Enum<?> e)
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
