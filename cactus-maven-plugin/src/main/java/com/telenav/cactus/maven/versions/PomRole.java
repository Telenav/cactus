package com.telenav.cactus.maven.versions;

import com.telenav.cactus.maven.model.Pom;
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
