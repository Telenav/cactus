package com.telenav.cactus.git;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableSet;

/**
 * Enough information about a commit to determine what projects it affected.
 *
 * @author Tim Boudreau
 */
public class CommitInfo
{
    private final String hash;
    private final Instant when;
    private final String author;
    private final String parent;
    private final Set<Path> paths;

    public CommitInfo(String hash, Instant when, String author, String parent,
            Set<Path> paths)
    {
        this.hash = hash;
        this.when = when;
        this.author = author;
        this.parent = parent;
        this.paths = paths;
    }

    public String info()
    {
        return when + " '" + author + "' " + hash;
    }

    public Set<Path> changedSubpaths()
    {
        return unmodifiableSet(paths);
    }

    public Instant when()
    {
        return when;
    }

    public String author()
    {
        return author;
    }

    public String parent()
    {
        return parent;
    }

    public String hash()
    {
        return hash;
    }

    public boolean isEmpty()
    {
        return paths.isEmpty();
    }

    public Optional<CommitInfo> javaSourceHistory()
    {
        return includeOnlyFileExtension(".java");
    }

    public Optional<CommitInfo> includeOnlyFileExtension(String ext)
    {
        String extF = ext.startsWith(".")
                      ? ext
                      : "." + ext;
        return filter(file -> file.getFileName().toString().endsWith(ext));
    }

    public Optional<CommitInfo> filter(Predicate<Path> test)
    {
        Set<Path> nue = paths.stream().filter(test).collect(Collectors
                .toCollection(HashSet::new));
        if (nue.isEmpty())
        {
            return Optional.empty();
        }
        return Optional.of(new CommitInfo(hash, when, author, parent, nue));
    }

    public boolean contains(Path checkoutRelativePath)
    {
        for (Path p : paths)
        {
            if (p.startsWith(checkoutRelativePath) || p.equals(
                    checkoutRelativePath))
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Visit specially formatted git log output to parse a commit and list
     * of changed files out of it.
     * 
     * @param output The output
     * @param test A test
     * @return the result of the predicate testing any commit message passed
     * to it - if it returns false, further parsing is aborted
     */
    static boolean visit(String output, Predicate<CommitInfo> test)
    {
        boolean result = true;

        CommitInfo currentHistory = null;

        Predicate<CommitInfo> emitter = hist ->
        {
            if (hist != null && !hist.isEmpty() && !test.test(hist))
            {
                return false;
            }
            return true;
        };

        for (String line : output.split("\n"))
        {
            if (line.isEmpty())
            {
                if (!emitter.test(currentHistory))
                {
                    currentHistory = null;
                    result = false;
                    break;
                }
                currentHistory = null;
                continue;
            }
            else
                if (line.startsWith("@^@:"))
                {
                    if (!emitter.test(currentHistory))
                    {
                        currentHistory = null;
                        result = false;
                        break;
                    }
                    line = line.substring(4);
                    String[] parts = line.split(":::");
                    // hash, date-offset-iso, author, parent-hash
                    if (parts.length == 4)
                    {
                        String hash = parts[0];
                        OffsetDateTime odt = OffsetDateTime.parse(parts[1]);
                        String author = parts[2];
                        String parentHash = parts[3];
                        currentHistory = new CommitInfo(hash, odt.toInstant(),
                                author, parentHash, new HashSet<>());
                    }
                }
                else
                {
                    if (currentHistory == null)
                    {
                        throw new IllegalArgumentException(
                                "Not a header or a path: '" + line + "'");
                    }
                    currentHistory.paths.add(Paths.get(line.trim()));
                }
        }
        if (result && currentHistory != null && !currentHistory.isEmpty())
        {
            result = test.test(currentHistory);
        }
        return result;
    }

    @Override
    public String toString()
    {
        return "CommitInfo{" + "hash=" + hash + ", when="
                + when + ", author=" + author + ", parent="
                + parent + ", paths=" + paths + '}';
    }
}
