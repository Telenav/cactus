package com.telenav.cactus.git;

import com.telenav.cactus.git.Conflicts.Conflict;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.unmodifiableList;

/**
 * A set of conflicts that prevent merging, as parsed from the output of git
 * merge-tree.
 *
 * @author Tim Boudreau
 */
public final class Conflicts implements Iterable<Conflict>
{
    private final List<Conflict> conflicts;
    public static final Conflicts EMPTY = new Conflicts(emptyList());

    private Conflicts(List<Conflict> conflicts)
    {
        this.conflicts = conflicts;
    }

    /**
     * In several places we special-case a conflict in .gitmodules because it is
     * nearly always solvable with a rebase, and these are incredibly common.
     *
     * @return True if the only file path in the conflict is .gitmodules
     */
    public boolean isGitmodulesOnlyConflict()
    {
        return !isEmpty() && singleton(Paths.get(".gitmodules"))
                .equals(allPaths());
    }

    /**
     * Get the set of file paths that have conflicts; in the case of renames or
     * deletions, these may contain paths that only exist in the remote or
     * local, or the string /dev/null.
     *
     * @return A set of paths
     */
    public Set<Path> allPaths()
    {
        Set<Path> result = new HashSet<>();
        for (Conflict cf : this)
        {
            result.addAll(cf.relativePaths());
        }
        return result;
    }

    /**
     * A conflict was encountered that actually resulted in conflict markers.
     *
     * @return true if there is a conflict in this set that cannot possibly be
     * resolved automatically by Git's ordinary mechanisms
     */
    public boolean hasHardConflicts()
    {
        if (isEmpty())
        {
            return false;
        }
        for (Conflict c : this)
        {
            if (c.isHardConflict())
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a Conflicts that omits any conflicts that did not contain
     * conflict markers (the same region of the file was not changed).
     *
     * @return A filtered set of conflicts, which may be empty.
     */
    public Conflicts filterHard()
    {
        List<Conflict> result = new ArrayList<>();
        for (Conflict cf : this)
        {
            if (cf.isHardConflict())
            {
                result.add(cf);
            }
        }
        return result.equals(conflicts)
               ? this
               : result.isEmpty()
                 ? EMPTY
                 : new Conflicts(result);
    }

    public boolean isEmpty()
    {
        return conflicts.isEmpty();
    }

    @Override
    public Iterator<Conflict> iterator()
    {
        return unmodifiableList(conflicts).iterator();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Conflict cf : this)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(cf);
        }
        return sb.toString();
    }

    /**
     * Parse the output of <code>git merge-tree</code> into a Conflicts object.
     *
     * @param mergeTreeOutput The output
     * @return A Conflicts
     */
    public static Conflicts parse(String mergeTreeOutput)
    {
        if (mergeTreeOutput.trim().isEmpty())
        {
            return EMPTY;
        }
        List<Conflict> conflicts = new ArrayList<>();

        boolean expectingChange = false;
        List<ChangeInfo> changeInfos = new ArrayList<>();

        Runnable emitChangeInfos = () ->
        {
            if (!changeInfos.isEmpty())
            {
                conflicts.add(new Conflict(changeInfos));
                changeInfos.clear();
            }
        };

        Runnable markLastAsDefiniteConflict = () ->
        {
            if (!conflicts.isEmpty())
            {
                Conflict cf = conflicts.get(conflicts.size() - 1);
                cf.definiteConflict = true;
            }
        };

        for (String line : mergeTreeOutput.split("\n"))
        {
            // Do this test before trimming:
            if (line.startsWith("+<<<<<<<")
                    || line.startsWith("+>>>>>>>")
                    || line.startsWith("+======="))
            {
                markLastAsDefiniteConflict.run();
                continue;
            }
            if (line.isBlank())
            {
                continue;
            }
            if (expectingChange)
            {
                ChangeInfo info = ChangeInfo.parse(line);
                if (info == null)
                {
                    emitChangeInfos.run();
                    expectingChange = false;
                }
                else
                {
                    changeInfos.add(info);
                }
            }
            else
                if ("changed in both".equals(line))
                {
                    emitChangeInfos.run();
                    expectingChange = true;
                }
        }
        emitChangeInfos.run();
        return conflicts.isEmpty()
               ? EMPTY
               : new Conflicts(conflicts);
    }

    /**
     * A single file conflict from a diff; consists of a set of ChangeInfos
     * which encode the file path, hash and permissions of one participant in
     * the conflict.
     */
    public static final class Conflict implements Iterable<ChangeInfo>
    {
        private final List<ChangeInfo> between;
        boolean definiteConflict;

        Conflict(List<ChangeInfo> between)
        {
            this.between = new ArrayList<>(between);
        }

        /**
         * Get the set of changed paths - typically there is only one path,
         * unless something was deleted or renamed, in which they may differ,
         * and one of them may be /dev/null.
         *
         * @return A set of affected paths
         */
        public Set<Path> relativePaths()
        {
            Set<Path> result = new TreeSet<>();
            for (ChangeInfo info : this)
            {
                if (info.relativePath != null && !info.relativePath.isBlank())
                {
                    result.add(Paths.get(info.relativePath));
                }
            }
            return result;
        }

        public Optional<ChangeInfo> local()
        {
            return get("our"); // constant git uses
        }

        public Optional<ChangeInfo> remote()
        {
            return get("their"); // constant git uses
        }

        public Optional<ChangeInfo> base()
        {
            return get("base"); // constant git uses
        }

        public Optional<ChangeInfo> get(String in)
        {
            for (ChangeInfo ci : this)
            {
                if (in.equals(ci.in))
                {
                    return Optional.of(ci);
                }
            }
            return Optional.empty();
        }

        public boolean isHardConflict()
        {
            return definiteConflict;
        }

        @Override
        public Iterator<ChangeInfo> iterator()
        {
            return unmodifiableList(between).iterator();
        }

        public String toString()
        {
            StringBuilder sb = new StringBuilder("changed in both");
            for (ChangeInfo info : this)
            {
                sb.append('\n').append(info);
            }
            return sb.toString();
        }
    }

    /**
     * Information about a single change that participates in a conflict.
     */
    public static final class ChangeInfo
    {
        /**
         * The integer file permissions / mode provided by git merge-tree.
         */
        public final int fileMode;
        /**
         * Git's string that identifies the logical origin - base, our or their.
         */
        public final String in;
        /**
         * The commit hash.
         */
        public final String commit;
        /**
         * The relative path.
         */
        public final String relativePath;

        public ChangeInfo(int fileMode, String in, String commit,
                String relativePath)
        {
            this.fileMode = fileMode;
            this.in = in;
            this.commit = commit;
            this.relativePath = relativePath;
        }

        public boolean isDeleted()
        {
            return relativePath != null && "/dev/null".equals(relativePath);
        }

        @Override
        public String toString()
        {
            return "  " + in + " " + fileMode + " " + commit + " " + relativePath;
        }

        public static final ChangeInfo parse(String line)
        {
            if (!line.startsWith("  "))
            {
                return null;
            }
            String[] words = line.trim().split("\\s+");
            if (words.length < 4)
            {
                return null;
            }
            switch (words[0])
            {
                case "base":
                case "our":
                case "their":
                    break;
                default:
                    return null;
            }
            int perms;
            try
            {
                perms = Integer.parseInt(words[1]);
            }
            catch (NumberFormatException nfe)
            {
                return null;
            }
            String hash = words[2];
            if (!GitCheckout.isGitCommitId(hash))
            {
                return null;
            }
            String relPath = words[3];
            if (words.length > 3)
            {
                for (int i = 4; i < words.length; i++)
                {
                    relPath = relPath + " " + words[i];
                }
            }
            ChangeInfo result = new ChangeInfo(perms, words[0], hash, relPath);
            return result;
        }
    }
}
