package com.telenav.cactus.maven.git;

import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.git.Branches.Branch;
import com.telenav.cactus.maven.util.PathUtils;
import com.telenav.cactus.maven.util.ProcessResultConverter;
import com.telenav.cactus.maven.util.ThrowingOptional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 *
 * @author Tim Boudreau
 */
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

    public static final GitCommand<Boolean> NO_MODIFICATIONS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().trueIfEmpty(),
                    "status", "--porcelain");

    public static final GitCommand<Map<String, GitRemotes>> LIST_REMOTES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(GitRemotes::from),
                    "remote", "-v");

    public static final GitCommand<Branches> ALL_BRANCHES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(Branches::from),
                    "branch", "--no-color", "-a");

    public static final GitCommand<Heads> REMOTE_HEADS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(Heads::from),
                    "ls-remote");

    public static final GitCommand<Boolean> IS_DIRTY
            = new GitCommand<>(ProcessResultConverter.exitCode(code -> code != 0),
                    "diff", "--quiet");

    public static final GitCommand<String> ADD_CHANGED
            = new GitCommand<>(ProcessResultConverter.strings(),
                    "add", "--all");

    public static final GitCommand<Boolean> PULL
            = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    "pull");

    public static final GitCommand<Boolean> PUSH
            = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    "push");

    public static final GitCommand<Boolean> IS_DETACHED_HEAD
            = new GitCommand<>(ProcessResultConverter.strings().testedWith(
                    text -> text.contains("(detached)")),
                    "status", "--porcelain=2", "--branch");

    public static final GitCommand<ZonedDateTime> COMMIT_DATE
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(GitCheckout::fromGitLogFormat),
                    "--no-pager", "log", "-1", "--format=format:%cd",
                    "--date=iso", "--no-color", "--encoding=utf8");

    private final GitCommand<List<SubmoduleStatus>> listSubmodules
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(this::parseSubmoduleInfo), "submodule", "status");

    private final BuildLog log = BuildLog.get();

    private final Path root;

    GitCheckout(Path root)
    {
        this.root = root;
    }

    private static ZonedDateTime fromGitLogFormat(String txt)
    {
        return ZonedDateTime.parse(txt, GIT_LOG_FORMAT);
    }

    public Branches branches()
    {
        return ALL_BRANCHES.withWorkingDir(root).run().awaitQuietly();
    }

    public static Optional<GitCheckout> repository(File dir)
    {
        return repository(dir.toPath());
    }

    public static Optional<GitCheckout> repository(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(GitCheckout::new);
    }

    public static Optional<GitCheckout> submodulesRoot(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(GitCheckout::new);
    }

    public Optional<GitRemotes> remote(String name)
    {
        return Optional.ofNullable(LIST_REMOTES.withWorkingDir(root).run().awaitQuietly().get(name));
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

    /**
     * The local directory name something is checked out into is not necessarily
     * related in any way to the project name, so when filtering which projects
     * we care about branch names in, treat the remote name as (at least
     * somewhat more) authoritative.
     *
     * @return A set of strings that are the final components of all git remote
     * urls configured for this repo, with any ".git" suffix trimmed
     */
    public Set<String> remoteProjectNames()
    {
        Set<String> result = new HashSet<>();
        allRemotes().forEach(remote -> remote.collectRemoteNames(result));
        return result;
    }

    public ZonedDateTime commitDate()
    {
        return COMMIT_DATE.withWorkingDir(root).run().awaitQuietly();
    }

    public Collection<? extends GitRemotes> allRemotes()
    {
        return LIST_REMOTES.withWorkingDir(root).run().awaitQuietly().values();
    }

    public GitCheckout updateRemoteHeads()
    {
        UPDATE_REMOTE_HEADS.withWorkingDir(root).run().awaitQuietly();
        return this;
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

    public Optional<String> mergeBase()
    {
        Branches branches = branches();
        return branches.currentBranch().flatMap(currentBranch ->
        {
            return branches.opposite(currentBranch).map(remoteBranch ->
            {
                return new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                        root, "merge-base", "@", remoteBranch.trackingName()).run().awaitQuietly();
            });
        });
    }

    public boolean push()
    {
        return PUSH.withWorkingDir(root).run().awaitQuietly();
    }

    public Heads remoteHeads()
    {
        return REMOTE_HEADS.withWorkingDir(root).run().awaitQuietly();
    }

    public String name()
    {
        return submoduleRoot().map(sroot -> sroot.equals(this) ? "" : sroot.checkoutRoot().relativize(root).toString())
                .orElse(root.getFileName().toString());
    }

    public boolean pushCreatingBranch()
    {
        Optional<String> branch = branch();
        if (!branch.isPresent())
        {
            return false;
        }
        Optional<GitRemotes> remote = this.defaultRemote();
        if (!remote.isPresent())
        {
            return false;
        }
        String output = new GitCommand<>(ProcessResultConverter.strings(), root,
                "push", "-u", remote.get().name, branch.get()).run().awaitQuietly();
        log.info(output);
        return true;
    }

    /**
     * Determines if a push is needed, and whether or not the remote branch
     * exists.
     *
     * @return A result
     */
    public NeedPushResult needsPush()
    {
        String remote = defaultRemote().map(rem -> rem.name()).orElse("origin");
        Branches branches = branches();
        if (!branches.currentBranch().isPresent())
        {
            return NeedPushResult.NOT_ON_A_BRANCH;
        }
        Branch br = branches.currentBranch().get();
        Optional<Branch> remBranch = branches.opposite(br);
        if (!remBranch.isPresent())
        {
            return NeedPushResult.REMOTE_BRANCH_DOES_NOT_EXIST;
        }
        boolean logWasEmpty = new GitCommand<>(ProcessResultConverter.strings().testedWith(String::isBlank),
                root, "log", remote + "/" + remBranch.get().name() + ".." + br.name()).run().awaitQuietly();
        return NeedPushResult.of(!logWasEmpty);
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

    /**
     * Updates .gitmodules so someone grabbing this branch and running git pull
     * in a submodule pulls from the correct remote branch.
     *
     * @param submodule The submodule - a path relative to the root
     * @param branch The branch name
     */
    public void setSubmoduleBranch(String submodule, String branch)
    {
        new GitCommand<>(ProcessResultConverter.strings(),
                root, "submodule", "set-branch", "-b", branch, submodule).run().awaitQuietly();
    }

    public boolean pull()
    {
        return PULL.withWorkingDir(root).run().awaitQuietly();
    }

    /**
     * Create a new branch (which must not yet exist) and switch to it. If a
     * remote branch of the same name exists, will automatically be set up to
     * track it.
     *
     * @param newLocalBranch The branch name to create
     * @return true if the command succeeds
     */
    public boolean createAndSwitchToBranch(String newLocalBranch, Optional<String> fallbackTrackingBranch)
    {
        return createAndSwitchToBranch(newLocalBranch, fallbackTrackingBranch, false);
    }

    public boolean createAndSwitchToBranch(String newLocalBranch, Optional<String> fallbackTrackingBranch, boolean pretend)
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
        Optional<Branch> remoteTrackingBranch = branches.find(newLocalBranch, false);
        if (!remoteTrackingBranch.isPresent())
        {
            if (fallbackTrackingBranch.isPresent())
            {
                String fallback = fallbackTrackingBranch.get();
                remoteTrackingBranch = branches.find(fallback, true);
                if (!remoteTrackingBranch.isPresent())
                {
                    remoteTrackingBranch = branches.find(fallback, false);
                }
                if (!remoteTrackingBranch.isPresent())
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
//            GitCommand<Boolean> cmd = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
//                    root,
//                    "checkout", "-b", newLocalBranch, "-t", track);
            GitCommand<String> cmd = new GitCommand<>(ProcessResultConverter.strings(),
                    root,
                    "checkout", "-b", newLocalBranch, "-t", track);
            cmd.run().awaitQuietly();
            return true;
        } else
        {
            if (pretend)
            {
                return true;
            }
            GitCommand<Boolean> cmd = new GitCommand<>(ProcessResultConverter.exitCodeIsZero(),
                    root,
                    "checkout", "-b", newLocalBranch);
            return cmd.run().awaitQuietly();
        }
    }

    public boolean hasPomInRoot()
    {
        return Files.exists(root.resolve("pom.xml"));
    }

    public boolean addAll()
    {
        ADD_CHANGED.withWorkingDir(root).run().awaitQuietly();
        return true;
    }

    public String head()
    {
        return GET_HEAD.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isInSyncWithRemoteHead()
    {
        Branches branches = branches();
        return branches.currentBranch().map(branch ->
        {
            System.out.println("  sync-check " + root.getFileName() + " branch " + branch);
            return branches.find(branch.name(), false).map(remoteBranch ->
            {
                String remoteHead = new GitCommand<>(ProcessResultConverter.strings().trimmed(), root, "rev-parse", remoteBranch.trackingName())
                        .run().awaitQuietly();
                String head = head();
                System.out.println("    remote-head " + remoteHead + " loc head " + head);
                return remoteHead.equals(head);
            }).orElse(false);
        }).orElse(false);
    }

    public Optional<String> remoteHead()
    {
        Branches branches = branches();
        return branches.currentBranch().flatMap(branch ->
        {
            return branches.find(branch.name(), false).map(remoteBranch ->
            {
                return new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                        root, "rev-parse", remoteBranch.trackingName())
                        .run().awaitQuietly();
            });
        });
    }

    public boolean add(Collection<? extends Path> paths)
    {
        List<String> list = new ArrayList<>(Arrays.asList("add"));
        for (Path path : paths)
        {
            if (path.isAbsolute())
            {
                path = root.relativize(path);
            }
            list.add(path.toString());
        }
        GitCommand<String> cmd = new GitCommand<>(ProcessResultConverter.strings(),
                root, list.toArray(String[]::new));
        String output = cmd.run().awaitQuietly();
        log.info(output);
        return true;
    }

    public boolean commit(String message)
    {
        String commitOut = new GitCommand<>(ProcessResultConverter.strings(), root,
                "commit", "-m", message).run().awaitQuietly();
        log.info(commitOut);
        return true;
    }

    public boolean hasUncommitedChanges()
    {
        return !NO_MODIFICATIONS.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isDirty()
    {
        return IS_DIRTY.withWorkingDir(root).run().awaitQuietly();
    }

    public boolean isDetachedHead()
    {
        return IS_DETACHED_HEAD.withWorkingDir(root).run().awaitQuietly();
    }

    public void scanForPomFiles(Consumer<Path> pomConsumer)
    {
        boolean isRoot = isSubmoduleRoot();

        Predicate<Path> isSameRoot = path ->
        {
            return !"target".equals(path.getFileName().toString()) && Files.isDirectory(path)
                    && (isRoot || (root.equals(path) || !Files.exists(path.resolve(".git"))));
        };
        if (hasPomInRoot())
        {
            pomConsumer.accept(root.resolve("pom.xml"));
        }
        try ( Stream<Path> str = Files.walk(root).filter(isSameRoot))
        {
            str.forEach(path ->
            {
                Path pom = path.resolve("pom.xml");
                if (Files.exists(pom) && (path.equals(root) || !Files.exists(path.resolve(".git"))))
                {
                    pomConsumer.accept(pom);
                }
            });
        } catch (IOException ioe)
        {
            Exceptions.chuck(ioe);
        }
    }

    public Set<Path> pomFiles(boolean fromRoot)
    {
        Set<Path> result = new HashSet<>();
        if (fromRoot)
        {
            submoduleRoot().ifPresent(gitRoot ->
            {
                gitRoot.scanForPomFiles(result::add);
            });
        } else
        {
            scanForPomFiles(result::add);
        }
        return result;
    }

    public boolean isSubmoduleRoot()
    {
        if (Files.exists(root.resolve(".gitmodules")))
        {
            Optional<Path> submoduleRoot = PathUtils.findGitCheckoutRoot(root, true);
            return submoduleRoot.isPresent() && root.equals(submoduleRoot.get());
        }
        return false;
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

    public Path checkoutRoot()
    {
        return root;
    }

    @Override
    public String toString()
    {
        return checkoutRoot().toString();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        } else if (o == null || o.getClass() != GitCheckout.class)
        {
            return false;
        }
        return ((GitCheckout) o).checkoutRoot().equals(checkoutRoot());
    }

    @Override
    public int hashCode()
    {
        return checkoutRoot().hashCode();
    }

    public static boolean isGitFlowInstalled()
    {
        return PathUtils.findExecutable("git-flow").isPresent();
    }

    public ThrowingOptional<List<SubmoduleStatus>> submodules()
    {
        if (isSubmoduleRoot())
        {
            List<SubmoduleStatus> infos = listSubmodules
                    .withWorkingDir(root)
                    .run()
                    .awaitQuietly();
            return infos.isEmpty() ? ThrowingOptional.empty() : ThrowingOptional.of(infos);
        } else
        {
            return submoduleRoot().flatMapThrowing(rootRepo -> rootRepo.submodules());
        }
    }

    List<SubmoduleStatus> parseSubmoduleInfo(String output)
    {
        return SubmoduleStatus.fromStatusOutput(root, output);
    }

    @Override
    public int compareTo(GitCheckout o)
    {
        return root.compareTo(o.root);
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
}
