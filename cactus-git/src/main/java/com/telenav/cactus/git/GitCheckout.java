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
package com.telenav.cactus.git;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.io.IOSupplier;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.cli.ProcessResultConverter;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.github.GithubCommand;
import com.telenav.cactus.github.MergePullRequestOptions;
import com.telenav.cactus.github.MinimalPRItem;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.util.PathUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static com.telenav.cactus.cli.ProcessResultConverter.*;
import static java.lang.Math.abs;
import static java.util.Optional.empty;

/**
 * @author Tim Boudreau
 */
@SuppressWarnings(
        {
            "unused", "UnusedReturnValue", "SwitchStatementWithTooFewBranches",
            "OptionalUsedAsFieldOrParameterType",
            "DuplicatedCode"
        })
public final class GitCheckout implements Comparable<GitCheckout>
{

    private static final DateTimeFormatter GIT_LOG_FORMAT = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR, 4).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral("-")
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendLiteral(' ')
            .appendOffset("+HHMM", "+0000")
            .parseLenient()
            .toFormatter();

    public static final DateTimeFormatter ISO_INSTANT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant()
            .toFormatter(Locale.US);

    public static final GitCommand<String> GET_BRANCH
            = new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                    "rev-parse", "--abbrev-ref", "HEAD");

    public static final GitCommand<String> GET_HEAD
            = new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                    "rev-parse", "HEAD");

    public static final GitCommand<Boolean> UPDATE_REMOTE_HEADS
            = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    "remote", "update");

    public static final GitCommand<Boolean> FETCH_ALL
            = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    "fetch", "--all");

    public static final GitCommand<Boolean> FETCH
            = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    "fetch");

    public static final GitCommand<Boolean> NO_MODIFICATIONS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .trueIfEmpty(),
                    "status", "--porcelain");

    public static final GitCommand<Map<String, GitRemotes>> LIST_REMOTES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(
                    GitRemotes::from),
                    "remote", "-v");

    public static final GitCommand<Branches> ALL_BRANCHES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(
                    Branches::from),
                    "branch", "--no-color", "-a");

    public static final GitCommand<Heads> REMOTE_HEADS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(
                    Heads::from),
                    "ls-remote");

    public static final GitCommand<Boolean> IS_DIRTY
            = new GitCommand<>(ProcessResultConverter
                    .exitCode(code -> code != 0),
                    "diff", "--quiet");

    public static final GitCommand<String> ADD_CHANGED
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "add", "--all");

    // Explictly pass --no-rebase, so a user's ~/.gitconfig cannot
    // alter plugin behavior
    public static final GitCommand<String> PULL
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "pull", "--no-rebase");

    public static final GitCommand<String> PULL_REBASE
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "pull", "--rebase");

    public static final GitCommand<String> PUSH
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "push");

    public static final GitCommand<String> PUSH_ALL
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "push", "--all");

    public static final GitCommand<String> GC
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "gc", "--aggressive");

    public static final GitCommand<List<String>> TAGS
            = new GitCommand<>(ProcessResultConverter.strings().lines(),
                    "tag", "-l");

    public static final GitCommand<Boolean> HAS_UNKNOWN_FILES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(
                    str -> str.length() > 0),
                    "ls-files", "--others", "--no-empty-directory",
                    "--exclude-standard");

    public static final GitCommand<Boolean> IS_DETACHED_HEAD
            = new GitCommand<>(ProcessResultConverter.strings().testedWith(
                    text -> text.contains("(detached)")),
                    "status", "--porcelain=2", "--branch");

    private static final ZoneId GMT = ZoneId.of("GMT");

    public static Optional<GitCheckout> checkout(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(GitCheckout::new);
    }

    public static Optional<GitCheckout> checkout(File dir)
    {
        return checkout(dir.toPath());
    }

    public static int depthFirstCompare(GitCheckout a, GitCheckout b)
    {
        int result = Integer.compare(b.checkoutRoot().getNameCount(),
                a.checkoutRoot().getNameCount());
        if (result == 0)
        {
            result = a.checkoutRoot().getFileName().compareTo(b.checkoutRoot()
                    .getFileName());
        }
        return result;
    }

    public static List<GitCheckout> depthFirstSort(
            Collection<? extends GitCheckout> all)
    {
        List<GitCheckout> checkouts = new ArrayList<>(all);
        checkouts.sort(GitCheckout::depthFirstCompare);
        return checkouts;
    }

    public static <R> List<Map.Entry<GitCheckout, R>> depthFirstSort(
            Map<GitCheckout, R> pushTypeForCheckout)
    {
        List<Map.Entry<GitCheckout, R>> needingPush = new ArrayList<>(
                pushTypeForCheckout.entrySet());
        // In case we have nested submodules, sort so we push deepest first
        needingPush.sort((a, b) -> GitCheckout.depthFirstCompare(a.getKey(), b
                .getKey()));
        return needingPush;
    }

    public static boolean isGitCommitId(String what)
    {
        if (what == null)
        {
            return false;
        }
        if (what.length() != 40)
        {
            return false;
        }
        for (int i = 0; i < what.length(); i++)
        {
            char c = what.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Collect the owning git checkouts of a collection of paths.
     *
     * @param paths Some paths
     * @return Some git checkouts
     */
    public static Set<GitCheckout> ownersOf(Collection<? extends Path> paths)
    {
        Set<GitCheckout> result = new HashSet<>();
        for (Path p : paths)
        {
            GitCheckout.checkout(p).ifPresent(result::add);
        }
        return result;
    }

    public static Optional<GitCheckout> submodulesRoot(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(GitCheckout::new);
    }

    private final BuildLog log = BuildLog.get();

    private final Path root;

    private final GitCommand<Optional<ZonedDateTime>> commitDate
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(this::fromGitLogFormat),
                    "log", "-1", "--format=format:%cd",
                    "--date=iso", "--no-color", "--encoding=utf8");

    private final GitCommand<List<SubmoduleStatus>> listSubmodules
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(this::parseSubmoduleInfo), "submodule", "status");

    GitCheckout(Path root)
    {
        this.root = notNull("root", root).normalize();
    }

    public boolean isNotAtSameHeadAsBranch(String otherBranch)
    {
        GitCommand<Boolean> isDirtyRelative
                = new GitCommand<>(exitCode(code -> code != 0), checkoutRoot(),
                        "diff", "--quiet", "-r", notNull("otherBranch",
                                otherBranch));
        return isDirtyRelative.run().awaitQuietly();
    }

    public boolean add(Collection<? extends Path> paths)
    {
        List<String> list = new ArrayList<>(List.of("add"));
        for (Path path : paths)
        {
            if (path.isAbsolute())
            {
                path = root.relativize(path);
            }
            list.add(path.toString());
        }
        GitCommand<String> cmd = new GitCommand<>(ProcessResultConverter
                .strings(),
                root, list.toArray(String[]::new));
        String output = cmd.run().awaitQuietly();
        log.info(output);
        return true;
    }

    public boolean addAll()
    {
        ADD_CHANGED.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    public void allPomFilesInSubtree(Consumer<Path> pomConsumer) throws IOException
    {
        Path pom = Paths.get("pom.xml");
        try ( Stream<Path> str = Files.walk(root).filter(path -> path
                .getFileName().equals(pom)))
        {
            str.forEach(pomConsumer);
        }
    }

    public void allPomFilesInSubtreeParallel(Consumer<Path> pomConsumer) throws IOException
    {
        Path pom = Paths.get("pom.xml");
        try ( Stream<Path> str = Files.walk(root).parallel().filter(path -> path
                .getFileName().equals(pom)))
        {
            str.forEach(pomConsumer);
        }
    }

    public Collection<? extends GitRemotes> allRemotes()
    {
        return LIST_REMOTES.withWorkingDir(root).run().awaitQuietly().values();
    }

    public Optional<String> branch()
    {
        String branch = GET_BRANCH.withWorkingDir(root).run().awaitQuietly();
        switch (branch)
        {
            case "HEAD": // this is the output if we are in detached-head mode
                return Optional.empty();
            default:
                return Optional.of(branch);
        }
    }

    public Branches branches()
    {
        return ALL_BRANCHES.withWorkingDir(root).run().awaitQuietly();
    }

    public List<String> tags()
    {
        return TAGS.withWorkingDir(root).run().awaitQuietly();
    }

    public Branches branchesContainingCommit(String commitHash)
    {
        GitCommand<Branches> targets = new GitCommand<>(strings().trimmed().map(
                Branches::from), checkoutRoot(),
                "branch", "--no-color", "--all", "--contains=" + commitHash);
        return targets.run().awaitQuietly();
    }

    public boolean pushTag(String tag)
    {
        Optional<GitRemotes> remote = defaultRemote();
        if (!remote.isPresent())
        {
            return false;
        }
        GitCommand<Boolean> cmd = new GitCommand<>(exitCodeIsZero(),
                checkoutRoot(), "push", remote.get().name(), tag);
        return cmd.run().awaitQuietly();
    }

    /**
     * Check if the working tree has conflicts with changes already fetched from
     * the remote.
     *
     * @return A conflicts, which will be empty if there are none and a merge
     * can be performed cleanly
     * @throws IllegalStateException if this repo is on no branch (detached
     * head)
     */
    public Conflicts checkForConflicts()
    {
        Optional<String> branch = branch();
        if (!branch.isPresent())
        {
            throw new IllegalStateException(loggingName()
                    + " is not on a branch - cannot check for merge conflicts");
        }
        GitCommand<String> fetchHeadCmd = new GitCommand<>(strings().trimmed(),
                checkoutRoot(), "merge-base", "FETCH_HEAD", branch.get());
        String mergeBase = fetchHeadCmd.run().awaitQuietly();
        GitCommand<String> inMemoryDiff = new GitCommand<>(strings(),
                checkoutRoot(), "merge-tree", mergeBase, branch.get(),
                "FETCH_HEAD");
        String diff = inMemoryDiff.run().awaitQuietly();
        return Conflicts.parse(diff);
    }

    private static String goodEnoughNonce()
    {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString(abs(ThreadLocalRandom.current().nextInt()),
                        36);
    }

    /**
     * Determines if local, uncommitted changes in the working tree can be
     * merged with the remote head of the current branch. Note there is no way
     * to do this with git <i>except by creating a commit</i> - this method will
     * create a temporary branch, do a commit and then use
     * <code>git merge-tree</code> and scan its output.
     * <p>
     * It has one side-effect: If some changes were staged in the index with
     * `git add`, they will be unstaged afterwards.
     *
     * @return A conflicts
     */
    public Conflicts canMergeWorkingTree()
    {
        // Okay, this is ugly but necessary.  Git merge-tree deals in trees - as
        // in things that have a hash and can be treated as a commit by git.  The
        // contents of the working tree are not that until they are added to the
        // index.
        //
        // So, if we want to test drive a merge, we have to actually create a
        // commit for it.
        Optional<String> branch = branch();
        if (!branch.isPresent())
        {
            throw new IllegalStateException(loggingName()
                    + " is not on a branch - cannot check for merge conflicts");
        }
        boolean wasDirty = isDirty();
        boolean hadUntrackedFiles = hasUntrackedFiles();
        if (!wasDirty && !hadUntrackedFiles)
        {
            return Conflicts.EMPTY;
        }
        log.debug(()
                -> loggingName() + " DIRTY? " + wasDirty + " UNTRACKED? " + hadUntrackedFiles);
        String origBranch = branch.get();
        String tempBranch = "_conflicttest-" + goodEnoughNonce();
        boolean committed = false;
        Throwable thrown = null;
        Conflicts result = Conflicts.EMPTY;
        try
        {
            log.debug(()
                    -> "create temp branch " + tempBranch + " for " + loggingName());
            boolean branched = createAndSwitchToBranch(tempBranch, empty());
            if (branched)
            {
                log.debug(() -> "  add all in " + loggingName());
                boolean added = addAll();
                if (!added)
                {
                    throw new IllegalStateException(
                            "Add all failed after creating branch " + tempBranch);
                }
                log.debug(() -> "  added? " + added);
                log.debug(() -> "  commit it " + loggingName());
                committed = commit(
                        "test-merge-commit of " + origBranch + " (will be removed)");
                if (committed)
                {
                    result = checkForConflicts();
                    log.debug("Committed.  Conflicts found: " + result);
                }
                else
                {
                    throw new IllegalStateException("Test commit on branch "
                            + tempBranch + " failed");
                }
            }
            else
            {
                throw new IllegalStateException("Creation of test branch "
                        + tempBranch + " failed");
            }
        }
        catch (Exception | Error e)
        {
            thrown = e;
        }
        finally
        {
            try
            {
                if (wasDirty != isDirty() || hadUntrackedFiles != hasUntrackedFiles())
                {
                    log.debug("Uncreate temp branch");
                    // Pending:  This is a little impolite in that if, on entry,
                    // there were some files added to the index with `git add`, they
                    // will no longer be added to the index.
                    GitCommand<Boolean> resetCommand = new GitCommand<>(
                            exitCodeIsZero(),
                            checkoutRoot(), "reset", "HEAD^");
                    boolean success = resetCommand.run().awaitQuietly();
                    log.debug(() -> "Reset test commit: " + success);
                    if (!success)
                    {
                        throw new IllegalStateException(
                                "Resetting test commit failed. "
                                + "Repository is left on test branch " + tempBranch + " "
                                + "coming from " + origBranch);
                    }
                }
                boolean deleted = deleteBranch(tempBranch, origBranch, true);
                if (!deleted)
                {
                    throw new IllegalStateException(
                            "Failed to delete conflict-test branch " + tempBranch);
                }
                log.debug(()
                        -> "deleted " + tempBranch + " in " + loggingName());
            }
            catch (Exception | Error e)
            {
                if (thrown != null)
                {
                    thrown.addSuppressed(e);
                }
                else
                {
                    thrown = e;
                }
            }
            if (thrown != null)
            {
                return chuck(thrown);
            }
        }
        return result;
    }

    public boolean canMerge(String mergeTo)
    {
        Throwable thrown = null;
        boolean result = false;
        // c.f. https://stackoverflow.com/questions/501407/is-there-a-git-merge-dry-run-option
        try
        {
            GitCommand<Boolean> trialMerge
                    = new GitCommand<>(exitCodeIsZero(), checkoutRoot(), "merge",
                            "--no-commit", "--no-ff");
            result = trialMerge.run().awaitQuietly();
        }
        catch (Exception | Error err)
        {
            thrown = err;
        }
        finally
        {
            GitCommand<Boolean> abortMerge
                    = new GitCommand<>(exitCodeIsZero(), checkoutRoot(), "merge",
                            "--abort");
            try
            {
                abortMerge.run().awaitQuietly();
            }
            catch (Exception | Error e1)
            {
                if (thrown != null)
                {
                    thrown.addSuppressed(e1);
                }
                else
                {
                    thrown = e1;
                }
            }
        }
        if (thrown != null)
        {
            // Avoid swallowing any error
            chuck(thrown);
        }
        return result;
    }

    public boolean checkoutOneFile(Path path)
    {
        if (!notNull("path", path).startsWith(checkoutRoot()))
        {
            throw new IllegalArgumentException(
                    path + " is not under checkout root " + checkoutRoot());
        }
        if (Files.exists(path) && Files.isDirectory(path))
        {
            throw new IllegalArgumentException(
                    "checkoutOneFile is for files but was passed a directory " + path);
        }
        String relPath = checkoutRoot().relativize(path).toString();
        GitCommand<String> checkoutOne = new GitCommand<>(ProcessResultConverter
                .strings(), checkoutRoot(), "checkout", relPath);
        // This will throw if non-zero
        String output = checkoutOne.run().awaitQuietly();
        log.child("checkout:" + relPath).info(output);
        return true;
    }

    public Path checkoutRoot()
    {
        return root;
    }

    public boolean commit(String message)
    {
        String commitOut = new GitCommand<>(ProcessResultConverter.strings(),
                root,
                "commit", "-m", message).run().awaitQuietly();
        log.info(commitOut);
        return true;
    }

    public Optional<ZonedDateTime> commitDate()
    {
        return commitDate.withWorkingDir(root).run().awaitQuietly();
    }

    @Override
    public int compareTo(GitCheckout o)
    {
        return root.compareTo(o.root);
    }

    public String commitMessage(String ref)
    {
        GitCommand<String> cmd = new GitCommand<>(strings().trimmed(),
                checkoutRoot(),
                "log",
                "--no-color",
                "-n", "1",
                "--pretty=format:%s",
                "-r", ref);

        return cmd.run().awaitQuietly();
    }

    public String headOf(String branchOrOtherRef)
    {
        return new GitCommand<>(
                strings().trimmed(),
                checkoutRoot(),
                "log",
                "--no-color",
                "-n", "1",
                "--pretty=format:%H",
                "-r", branchOrOtherRef)
                .run()
                .awaitQuietly();
    }

    /**
     * Visit the git change history of this git checkout. The arguments that
     * affect what commits <code>git log</code> finds are
     * <pre>
     * --topo-order --simplify-merges --no-abbrev --remove-empty
     * </pre>
     *
     * @param pageSize The number of log records to fetch from git before
     * calling the predicate with them - history can be very large, and if
     * searching for something specific (like the last code change for a
     * particular project), there is no need to read all of it
     * @param test A predicate which should return false to finish iterating and
     * paging
     */
    public void changeHistory(int pageSize, Predicate<CommitInfo> test)
    {
        /*
         Generates git log output that will look like this:

         @^@:f42517d96ae0a023a9852b8499c2fa0f3df4fda6:::2022-06-22T15:40:01-04:00:::Tim Boudreau:::b5324c24e2866f80772da922bc7549795c3c6a0f:::gn
         util-strings/src/main/java/com/mastfrog/util/strings/Strings.java
         util-whatever/pom.xml

         We use some odd delimiters because quotes can be present (esp if we
         decide to include the commit message later - I removed it for now)
         to ensure we don't collide with quotes or other more common sequences.
         */
        List<String> args = new ArrayList<>(Arrays.asList(
                "log",
                "--skip",
                "0",
                "--no-color",
                "-n",
                Integer.toString(pageSize),
                "--topo-order",
                "--simplify-merges",
                "--no-abbrev",
                "--remove-empty",
                "--pretty=format:@^@:%h:::%aI:::%an:::gn",
                "--dirstat-by-file=cumulative",
                "--name-only"
        ));
        GitCommand<String> cmd = new GitCommand<>(strings(), checkoutRoot(),
                args.toArray(String[]::new));

        int skip = pageSize;
        String output;
        do
        {
            output = cmd.run().awaitQuietly();
            if (!output.isBlank())
            {
                if (!CommitInfo.visit(output, test))
                {
                    break;
                }
                args.set(3, Integer.toString(skip));
                skip += pageSize;
                cmd = new GitCommand<>(strings(), checkoutRoot(), args.toArray(
                        String[]::new));
            }
        }
        while (!output.isBlank());
    }

    /**
     * Create a new branch (which must not yet exist) and switch to it. If a
     * remote branch of the same name exists, will automatically be set up to
     * track it.
     *
     * @param newLocalBranch The branch name to create
     * @return true if the command succeeds
     */
    public boolean createAndSwitchToBranch(String newLocalBranch,
            Optional<String> fallbackTrackingBranch)
    {
        return createAndSwitchToBranch(newLocalBranch, fallbackTrackingBranch,
                false);
    }

    public boolean createAndSwitchToBranch(String newLocalBranch,
            Optional<String> fallbackTrackingBranch,
            boolean pretend)
    {
        if (newLocalBranch.isEmpty())
        {
            throw new IllegalArgumentException("Empty new branch name");
        }
        Branches branches = branches();
        if (branches.find(newLocalBranch, true).isPresent())
        {
            throw new IllegalArgumentException(
                    "Already contains a local branch named '"
                    + newLocalBranch + "': " + this);
        }
        Optional<Branch> remoteTrackingBranch = branches.find(newLocalBranch,
                false);
        if (remoteTrackingBranch.isEmpty())
        {
            if (fallbackTrackingBranch.isPresent())
            {
                String fallback = fallbackTrackingBranch.get();
                remoteTrackingBranch = branches.find(fallback, true);
                if (remoteTrackingBranch.isEmpty())
                {
                    remoteTrackingBranch = branches.find(fallback, false);
                }
                if (remoteTrackingBranch.isEmpty())
                {
                    log.warn("Could not find a branch to track for '"
                            + newLocalBranch
                            + "' or a local or remote branch named '"
                            + fallback + "'");
                }
            }
        }
        if (remoteTrackingBranch.isPresent())
        {
            String track = remoteTrackingBranch.get().trackingName();
            if (pretend)
            {
                return true;
            }
            GitCommand<String> cmd = new GitCommand<>(ProcessResultConverter
                    .strings(),
                    root,
                    "checkout", "-b", newLocalBranch, "-t", track);
            cmd.run().awaitQuietly();
            return true;
        }
        else
        {
            if (pretend)
            {
                return true;
            }
            GitCommand<Boolean> cmd = new GitCommand<>(ProcessResultConverter
                    .exitCodeIsZero(),
                    root,
                    "checkout", "-b", newLocalBranch);
            return cmd.run().awaitQuietly();
        }
    }

    public Optional<GitRemotes> defaultRemote()
    {
        Collection<? extends GitRemotes> remotes = allRemotes();
        if (remotes.isEmpty())
        {
            return Optional.empty();
        }
        for (GitRemotes rem : remotes)
        {
            if ("origin".equals(rem.name))
            {
                return Optional.of(rem);
            }
        }
        return Optional.of(remotes.iterator().next());
    }

    public boolean deleteBranch(String branchToDelete, String branchToMoveTo,
            boolean force)
    {
        Optional<String> currentBranch = this.branch();
        if (currentBranch.isEmpty() || !currentBranch.get().equals(
                branchToMoveTo))
        {
            this.switchToBranch(branchToMoveTo);
        }

        GitCommand<String> gc = new GitCommand<>(
                ProcessResultConverter.strings(),
                checkoutRoot(),
                "branch", (force
                           ? "-D"
                           : "-d"), branchToDelete);

        String output = gc.run().awaitQuietly();
        log.child("delete-branch:" + branchToDelete).info(output);

        return true;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
        {
            if (o == null || o.getClass() != GitCheckout.class)
            {
                return false;
            }
        }
        return ((GitCheckout) o).checkoutRoot().equals(checkoutRoot());
    }

    public boolean fetchAll()
    {
        return FETCH_ALL.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean fetch()
    {
        return FETCH.withWorkingDir(root).run().awaitQuietly();
    }

    public void gc()
    {
        GC.withWorkingDir(checkoutRoot()).run().awaitQuietly();
    }

    public boolean hasPomInRoot()
    {
        return Files.exists(root.resolve("pom.xml"));
    }

    public boolean hasUncommitedChanges()
    {
        return !NO_MODIFICATIONS.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean hasUntrackedFiles()
    {
        return HAS_UNKNOWN_FILES.withWorkingDir(root).run().awaitQuietly();
    }

    @Override
    public int hashCode()
    {
        return checkoutRoot().hashCode();
    }

    public String head()
    {
        return GET_HEAD.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isBranch(String branch)
    {
        return branch().filter(branch::equals).isPresent();
    }

    public boolean isDetachedHead()
    {
        return IS_DETACHED_HEAD.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isDirty()
    {
        return IS_DIRTY.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isInSyncWithRemoteHead()
    {
        Branches branches = branches();
        return branches.currentBranch().map(branch ->
        {
            return branches.find(branch.name(), false).map(remoteBranch ->
            {
                String remoteHead = new GitCommand<>(ProcessResultConverter
                        .strings().trimmed(), root, "rev-parse", remoteBranch
                                .trackingName())
                        .run().awaitQuietly();
                String head = head();
                return remoteHead.equals(head);
            }).orElse(false);
        }).orElse(false);
    }

    public boolean isRoot()
    {
        Path par = checkoutRoot().getParent();
        if (par == null)
        {
            return true;
        }
        return PathUtils.findGitCheckoutRoot(par, true).isEmpty();
    }

    public boolean isSubmodule()
    {
        Optional<GitCheckout> par = PathUtils.findParentWithChild(checkoutRoot()
                .getParent(), PathUtils.FileKind.FILE,
                ".gitmodules")
                .flatMap(GitCheckout::checkout);
        return par.map(co ->
        {
            return co.submodules().map(subs ->
            {
                for (SubmoduleStatus stat : subs)
                {
                    if (stat.checkout().isPresent() && equals(stat
                            .checkout().get()))
                    {
                        return true;
                    }
                }
                return false;
            }).orElse(false);
        }).orElse(false);
    }

    public boolean isSubmoduleRoot()
    {
        if (Files.exists(root.resolve(".gitmodules")))
        {
            Optional<Path> submoduleRoot = PathUtils.findGitCheckoutRoot(root,
                    true);
            return submoduleRoot.isPresent() && root.equals(submoduleRoot.get());
        }
        return false;
    }

    public String loggingName()
    {
        String n = name();
        if (n.isEmpty())
        {
            n = checkoutRoot().getFileName().toString();
        }
        return n;
    }

    public boolean merge(String branch)
    {
        new GitCommand<>(ProcessResultConverter.strings(),
                root, "merge", branch).run().awaitQuietly();
        return true;
    }

    public Optional<String> mergeBase()
    {
        Branches branches = branches();
        return branches.currentBranch().flatMap(currentBranch
                -> branches.opposite(currentBranch).map(remoteBranch
                        -> new GitCommand<>(ProcessResultConverter.strings()
                        .trimmed(),
                        root, "merge-base", "@", remoteBranch.trackingName())
                        .run().awaitQuietly()));
    }

    public String name()
    {
        return submoduleRoot().map(sroot -> sroot.equals(this)
                                            ? ""
                                            : sroot.checkoutRoot().relativize(
                        root).toString())
                .orElse(root.getFileName().toString());
    }

    public boolean needsPull()
    {
        String head = head();
        if (head == null)
        {
            // bare repository
            return false;
        }
        Branches branches = branches();
        Optional<Branch> curr = branches.currentBranch();
        if (!curr.isPresent())
        {
            // detached head = no place to pull from
            return false;
        }

        Branch branch = curr.get();
        // Pending - we could use git branch -vv
        // to get the tracking branch for the branch even if it is
        // named differently than the local one.        
        Optional<Branch> remBranch = branches.find(branch.name(), false);
        if (!remBranch.isPresent())
        {
            // This is a local only branch - nothing to pull from
            return false;
        }

        String remoteHead = headOf(remBranch.get().trackingName());
        if (remoteHead == null)
        {
            // should not happen
            return false;
        }

        // If our head is not the same hash as the head of the remote
        // branch, then we might need to pull
        if (remoteHead.equals(head))
        {
            return false;
        }
        // Okay, now we know the remote head and the head we're looking
        // at are not the same.  That could mean that there are remote
        // commits we need to pull, or that could mean that there are
        // unpushed local commits, in which case we don't.
        //
        // So, if the local head is an ancestor of the remote head, then
        // there are commits to pull, and if not - or vice versa, then
        // there are commits to push (or history has been altered)
        String tname = remBranch.get().trackingName();
        boolean result = isAncestor("HEAD", tname);
        return result;
    }

    /**
     * Determine if one commit or branch name or similar is an ancestor of
     * another.
     *
     * @param proposedParentCommitOrRef A hash or ref name
     * @param proposedChildCommitOrRef A hash or ref name
     * @return
     */
    public boolean isAncestor(String proposedParentCommitOrRef,
            String proposedChildCommitOrRef)
    {
        return new GitCommand<>(
                exitCodeIsZero(),
                checkoutRoot(),
                "merge-base",
                "--is-ancestor",
                notNull("proposedParentCommitOrRef", proposedParentCommitOrRef),
                notNull("proposedChildCommitOrRef", proposedChildCommitOrRef)
        ).run().awaitQuietly();
    }

    /**
     * Determines if a push is needed, and whether or not the remote branch
     * exists.
     *
     * @return A result
     */
    public NeedPushResult needsPush()
    {
        String remote = defaultRemote().map(GitRemotes::name).orElse("origin");
        Branches branches = branches();
        if (branches.currentBranch().isEmpty())
        {
            return NeedPushResult.NOT_ON_A_BRANCH;
        }
        Branch br = branches.currentBranch().get();
        Optional<Branch> remBranch = branches.opposite(br);
        if (remBranch.isEmpty())
        {
            return NeedPushResult.REMOTE_BRANCH_DOES_NOT_EXIST;
        }
        boolean logWasEmpty = new GitCommand<>(ProcessResultConverter.strings()
                .testedWith(String::isBlank),
                root, "log", remote + "/" + remBranch.get().name() + ".." + br
                .name()).run().awaitQuietly();
        return NeedPushResult.of(!logWasEmpty);
    }

    public boolean noPomInRoot()
    {
        return !hasPomInRoot();
    }

    public Set<Path> pomFiles(boolean fromRoot)
    {
        Set<Path> result = new HashSet<>();
        if (fromRoot)
        {
            submoduleRoot().ifPresent(gitRoot -> gitRoot.scanForPomFiles(
                    result::add));
        }
        else
        {
            scanForPomFiles(result::add);
        }
        return result;
    }

    public boolean pull()
    {
        PULL.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    public boolean pullWithRebase()
    {
        PULL_REBASE.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    /**
     * Creates a pull request on Github using the given authentication token,
     * title and body
     *
     * @param authenticationToken A supplier that can provide a GitHub personal
     * access token, or null if none is needed or can be provided
     * @param reviewers A comma-separated list of Github reviewer handles
     * @param title The title of the pull request - if null, body must also be
     * null
     * @param body The body of the pull request
     * @param sourceBranch The origin branch
     * @param destBranch The target branch for the pull request
     * @return True if the pull request was created
     */
    public URI createPullRequest(IOSupplier<String> authenticationToken,
            String reviewers,
            String title,
            String body,
            String sourceBranch,
            String destBranch)
    {
        if ((title == null) != (body == null))
        {
            throw new IllegalArgumentException(
                    "Either title and body must both be "
                    + "non-null, or both null, but got '" + title + "' and '"
                    + body + '\'');
        }

        var arguments = new ArrayList<String>();
        arguments.add("pr");
        arguments.add("create");
        arguments.add("--base");
        arguments.add(destBranch);
        arguments.add("--head");
        arguments.add(sourceBranch);
        if (title != null)
        {
            arguments.add("--title");
            arguments.add("\"" + title + "\"");
            arguments.add("--body");
            arguments.add("\"" + body + "\"");
        }
        else
        {
            arguments.add("-f");
        }
        if (reviewers != null && !reviewers.isBlank())
        {
            for (var reviewer : reviewers.split(","))
            {
                if (!reviewer.isBlank())
                {
                    arguments.add("--reviewer");
                    arguments.add(reviewer.trim());
                }
            }
        }

        // (gh pr --title "$title" --body "$body" --reviewer yyzhou --reviewer jonathanl-telenav)
        //
        // Use a timeout here, because `gh` has no non-interactive mode, and if it
        // is trying to ask a question, it will hang forever
        return new GithubCommand<URI>(authenticationToken,
                trailingUriWithTrailingDigitAloneOnLine(), root,
                arguments.toArray(String[]::new)).run().awaitQuietly(Duration
                .ofMinutes(2));
    }

    /**
     * Merges pull request on Github using the given authentication token
     *
     * @param personalAcccessTokenSupplier Can supply
     * @param branchName The name of the branch to merge
     * @param options The options to pass to the github cli
     * @return True if the pull request was merged
     */
    public boolean mergePullRequest(
            IOSupplier<String> personalAcccessTokenSupplier,
            String branchName,
            Set<MergePullRequestOptions> options)
    {
        // Sign into Github (gh auth login --hostname github.com --with-token < ~/token.txt)
        var arguments = new ArrayList<String>();
        arguments.add("pr");
        arguments.add("merge");
        options.forEach(opt -> opt.accept(arguments));
        arguments.add("--body");
        arguments.add("Merge " + branchName);

        // Create pull request (gh pr merge --auto --merge --squash --body "Merge feature/reverse-string)
        new GithubCommand<>(personalAcccessTokenSupplier, strings(), root,
                arguments.toArray(String[]::new)).run().awaitQuietly();

        return true;
    }
    /**
     * Merges pull request on Github using the given authentication token
     *
     * @param personalAcccessTokenSupplier Can supply
     * @param branchName The name of the branch to merge
     * @param body Any text to describe the approval
     * @return True if the pull request was merged
     */
    public boolean approvePullRequest(
            IOSupplier<String> personalAcccessTokenSupplier,
            String branchName,
            Optional<String> body
    )
    {
        // Sign into Github (gh auth login --hostname github.com --with-token < ~/token.txt)
        var arguments = new ArrayList<String>();
        arguments.add("pr");
        arguments.add("review");
        arguments.add("--approve");
        arguments.add("--body");
        arguments.add(body.orElse("Approved " + branchName));

        // Create pull request (gh pr review --approve --body "Approved feature/reverse-string")
        new GithubCommand<>(personalAcccessTokenSupplier, strings(), root,
                arguments.toArray(String[]::new)).run().awaitQuietly();

        return true;
    }

    public List<MinimalPRItem> listPullRequests(
            IOSupplier<String> personalAcccessTokenSupplier,
            String destBranchFilter, String prBranchFilter, String searchFilter)
    {
        var arguments = new ArrayList<String>();
        arguments.add("pr");
        arguments.add("list");
        if (destBranchFilter != null && !destBranchFilter.isBlank())
        {
            arguments.add("--base");
            arguments.add(destBranchFilter);
        }
        if (prBranchFilter != null && !prBranchFilter.isBlank())
        {
            arguments.add("--head");
            arguments.add(prBranchFilter);
        }
        if (searchFilter != null && !searchFilter.isBlank())
        {
            arguments.add("--search");
            arguments.add(searchFilter);
        }
        arguments.add("--json");
        arguments.add(
                "url,title,state,mergeable,body,number,headRefName,baseRefName");

        return new GithubCommand<>(personalAcccessTokenSupplier,
                strings().map(MinimalPRItem.parser()), root,
                arguments.toArray(String[]::new)).run().awaitQuietly();
    }

    public boolean push()
    {
        PUSH.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    public boolean pushAll()
    {
        PUSH_ALL.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    public boolean pushCreatingBranch()
    {
        Optional<String> branch = branch();
        if (branch.isEmpty())
        {
            return false;
        }
        Optional<GitRemotes> remote = this.defaultRemote();
        if (remote.isEmpty())
        {
            return false;
        }
        String output = new GitCommand<>(ProcessResultConverter.strings(), root,
                "push", "-u", remote.get().name, branch.get()).run()
                .awaitQuietly();
        log.info(output);
        return true;
    }

    public Optional<GitRemotes> remote(String name)
    {
        return Optional.ofNullable(LIST_REMOTES.withWorkingDir(root).run()
                .awaitQuietly().get(name));
    }

    public Optional<String> trackingBranch()
    {
        return branch().flatMap(this::trackingBranchOf);
    }

    public Optional<String> trackingBranchOf(String branch)
    {
        ProcessResultConverter<Optional<String>> cvt = strings().trimmed().map(
                str ->
        {
            String[] parts = str.split("\\s");
            if (parts.length != 2)
            {
                return Optional.empty();
            }
            String h = headOf(parts[1]);
            return h == null || h.isBlank()
                   ? empty()
                   : Optional.of(h);
        });
        return new GitCommand<>(cvt, checkoutRoot(),
                "branch", "--format", "%(refname:short) %(upstream:short)",
                "--list", branch).run().awaitQuietly();
    }

    public Optional<String> remoteHead()
    {
        Branches branches = branches();
        log.debug(
                () -> "RemoteHead of " + loggingName() + " - branches:\n" + branches);
        return branches.currentBranch().flatMap(branch
                ->
        {
            log.debug(() -> "  have branch " + branch);
            Optional<Branch> remBranch = branches.find(branch.name(), false);
            if (remBranch.isPresent())
            {
                log.debug(() -> "  have remote branch " + remBranch.get()
                        + " with tracking name " + remBranch.get()
                                .trackingName());

                String result = new GitCommand<>(strings().trimmed(),
                        root, "rev-parse", remBranch.get().trackingName())
                        .run().awaitQuietly();
                log.debug(() -> "  rev-parse result '" + result + "'");
                return result == null || result.isBlank()
                       ? Optional.empty()
                       : Optional.of(result);
            }
            else
            {
                log.debug(() -> "  no remote branch for " + branch);
                return empty();
            }
        });

        /*
        
        
        Optional<String> br = branch();
        if (!br.isPresent())
        {
            return empty();
        }
        ProcessResultConverter<Optional<String>> cvt = strings().trimmed().map(
                str ->
        {
            String[] parts = str.split("\\s");
            if (parts.length != 2)
            {
                return Optional.empty();
            }
            String h = headOf(parts[1]);
            return h == null || h.isBlank()
                   ? empty()
                   : Optional.of(h);
        });
        return new GitCommand<>(cvt, checkoutRoot(),
                "branch", "--format", "%(refname:short) %(upstream:short)",
                "--list", br.get()).run().awaitQuietly();
         */
 /*
        Branches branches = branches();
        return branches.currentBranch().flatMap(branch
                -> branches.find(branch.name(), false).map(remoteBranch
                        -> new GitCommand<>(strings().trimmed(),
                        root, "rev-parse", remoteBranch.trackingName())
                        .run().awaitQuietly()));
         */
    }

    public Optional<String> fetchHead()
    {
        return new GitCommand<>(nonEmptyString(), checkoutRoot(),
                "rev-parse", "FETCH_HEAD").run().awaitQuietly();
    }

    public Heads remoteHeads()
    {
        return REMOTE_HEADS.withWorkingDir(root).run().awaitQuietly();
    }

    /**
     * The local directory name something is checked out into is not necessarily
     * related in any way to the project name, so when filtering which projects
     * we care about branch names in, treat the remote name as (at least
     * somewhat more) authoritative.
     *
     * @return A set of strings that are the final components of all git remote
     * urls configure d for this repo, with any ".git" suffix trimmed
     */
    public Set<String> remoteProjectNames()
    {
        Set<String> result = new HashSet<>();
        allRemotes().forEach(remote -> remote.collectRemoteNames(result));
        return result;
    }

    public void scanForPomFiles(Consumer<Path> pomConsumer)
    {
        boolean isRoot = isSubmoduleRoot();

        Predicate<Path> isSameRoot = path
                -> !"target".equals(path.getFileName().toString()) && Files
                .isDirectory(path)
                && (isRoot || (root.equals(path) || !Files.exists(path.resolve(
                ".git"))));
        if (hasPomInRoot())
        {
            pomConsumer.accept(root.resolve("pom.xml"));
        }
        try ( Stream<Path> str = Files.walk(root).filter(isSameRoot))
        {
            str.forEach(path ->
            {
                Path pom = path.resolve("pom.xml");
                if (Files.exists(pom) && (path.equals(root) || !Files.exists(
                        path.resolve(".git"))))
                {
                    pomConsumer.accept(pom);
                }
            });
        }
        catch (IOException ioe)
        {
            Exceptions.chuck(ioe);
        }
    }

    /**
     * Updates .gitmodules so someone grabbing this branch and running git pull
     * in a submodule pulls from the correct remote branch.
     *
     * @param submodule The submodule - a path relative to the root
     * @param branch The branch name
     */
    public void setSubmoduleBranch(String submodule, String branch)
    {
        if (submodule == null || submodule.isEmpty())
        {
            throw new IllegalArgumentException("Missing submodule");
        }
        if (branch == null || branch.isEmpty())
        {
            throw new IllegalArgumentException("Missing submodule");
        }
        new GitCommand<>(ProcessResultConverter.strings(),
                root, "submodule", "set-branch", "-b", branch, submodule).run()
                .awaitQuietly();
    }

    public ThrowingOptional<Path> submoduleRelativePath()
    {
        if (isSubmoduleRoot())
        {
            return ThrowingOptional.empty();
        }
        return submoduleRoot().map(
                rootCheckout -> rootCheckout.checkoutRoot().relativize(root));
    }

    public ThrowingOptional<GitCheckout> submoduleRoot()
    {
        return ThrowingOptional.from(PathUtils.findGitCheckoutRoot(root, true))
                .map((Path dir) ->
                {
                    if (dir.equals(root))
                    {
                        return this;
                    }
                    return new GitCheckout(dir);
                });
    }

    public ThrowingOptional<List<SubmoduleStatus>> submodules()
    {
        if (isSubmoduleRoot())
        {
            List<SubmoduleStatus> infos = listSubmodules
                    .withWorkingDir(root)
                    .run()
                    .awaitQuietly();
            return infos.isEmpty()
                   ? ThrowingOptional.empty()
                   : ThrowingOptional.of(infos);
        }
        else
            if (isSubmodule())
            {
                return submoduleRoot().flatMapThrowing(root ->
                {
                    return root == this
                           ? ThrowingOptional.empty()
                           : root.submodules();
                });
            }
        return ThrowingOptional.empty();
    }

    /**
     * Switch to a local branch (which must already exist).
     *
     * @param localBranch A branch
     * @return true if the command succeeds
     */
    public boolean switchToBranch(String localBranch)
    {
        return new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                root, "checkout", localBranch).run().awaitQuietly();
    }

    public boolean tag(String tagName, boolean force)
    {
        GitCommand<Boolean> tag;
        if (force)
        {
            tag = new GitCommand<>(exitCodeIsZero(),
                    checkoutRoot(), "tag", "-f", tagName);
        }
        else
        {
            tag = new GitCommand<>(exitCodeIsZero(),
                    checkoutRoot(), "tag", tagName);
        }
        return tag.run().awaitQuietly();
    }

    @Override
    public String toString()
    {
        return checkoutRoot().toString();
    }

    public GitCheckout updateRemoteHeads()
    {
        UPDATE_REMOTE_HEADS.withWorkingDir(root).run().awaitQuietly();
        return this;
    }

    List<SubmoduleStatus> parseSubmoduleInfo(String output)
    {
        return SubmoduleStatus.fromStatusOutput(root, output);
    }

    private Optional<ZonedDateTime> fromGitLogFormat(String txt)
    {
        if (txt.isEmpty())
        {
            log.error("Got an empty timestamp from git log for " + root
                    + " branch " + branch() + " head " + head());
            return Optional.empty();
        }
        try
        {
            return Optional.of(
                    ZonedDateTime.parse(txt, GIT_LOG_FORMAT)
                            .withZoneSameInstant(GMT));
        }
        catch (DateTimeParseException ex)
        {
            log.error("Failed to parse git log date string '" + txt + "'", ex);
            return Optional.empty();
        }
    }
}
