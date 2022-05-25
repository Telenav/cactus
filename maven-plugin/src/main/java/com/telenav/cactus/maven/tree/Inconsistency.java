package com.telenav.cactus.maven.tree;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * A problem with branch or version consistency within a set of checked out
 * projects.
 */
public class Inconsistency<T>
{

    private final Map<String, Set<T>> partitions;
    private final Kind kind;
    private final Function<T, Path> pathConverter;

    Inconsistency(Map<String, Set<T>> partitions, Kind kind, Function<T, Path> pathConverter)
    {
        this.partitions = partitions;
        this.kind = kind;
        this.pathConverter = pathConverter;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(kind.toString()).append(':');
        partitions.forEach((name, partition) ->
        {
            sb.append("\n  * ").append(name).append('=');
            writeCommaDelimited(partition, sb);
            sb.append('\n');
        });
        return sb.toString();
    }

    /**
     * Get the set of paths that are outliers for this issue.
     *
     * @return A set of paths
     */
    public Set<Path> outlierPaths()
    {
        Set<Path> result = new HashSet<>();
        Set<String> outliers = outlierPartitions();
        for (String outlier : outliers)
        {
            for (T partition : partitions.get(outlier))
            {
                result.add(pathConverter.apply(partition));
            }
        }
        return result;
    }

    public Optional<String> commonalityPartition()
    {
        Set<String> all = new HashSet<>(partitions.keySet());
        all.removeAll(outlierPartitions());
        return all.size() == 1
                ? Optional.of(all.iterator().next())
                : Optional.empty();
    }

    /**
     * Find the partitions which have less than the greatest number of entries
     * These are the ones (usually 1-2) that it would be the most minimal change
     * to bring into consistency with others
     *
     * @return A set of those partition keys which have less than the set
     * belonging to the partition key associated with the largest set
     */
    private Set<String> outlierPartitions()
    {
        // Find the partitions which have less than the greatest number of entries
        // These are the ones (usually 1-2) that it would be the most minimal change
        // to bring into consistency with others
        if (partitions.size() < 2)
        {
            return partitions.keySet();
        }
        List<Map.Entry<String, Set<T>>> entries = new ArrayList<>(partitions.entrySet());
        Collections.sort(entries, (a, b) ->
        {
            // reverse sort
            return Integer.compare(b.getValue().size(), a.getValue().size());
        });
        entries.remove(0);
        Set<String> result = new TreeSet<>();
        entries.forEach(e -> result.add(e.getKey()));
        return result;
    }

    private static <T> void writeCommaDelimited(Set<T> set, StringBuilder into)
    {
        for (Iterator<T> iter = set.iterator(); iter.hasNext();)
        {
            into.append(iter.next());
            if (iter.hasNext())
            {
                into.append(',');
            }
        }
    }

    /**
     * The kind of consistency failure encountered.
     */
    public enum Kind
    {
        VERSION,
        BRANCH,
        CONTAINS_MODIFIED_SOURCES,
        NOT_ON_A_BRANCH;

        @Override
        public String toString()
        {
            switch (this)
            {
                case VERSION:
                    return "More than one version present";
                case BRANCH:
                    return "More than one branch present";
                case CONTAINS_MODIFIED_SOURCES:
                    return "Modified, uncommitted sources present";
                case NOT_ON_A_BRANCH:
                    return "Checkout is in detached head mode - not on a branch";
                default:
                    throw new AssertionError(this);
            }
        }
    }
}
