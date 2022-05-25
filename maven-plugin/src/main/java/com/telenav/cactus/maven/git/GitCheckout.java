package com.telenav.cactus.maven.git;

import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.util.PathUtils;
import com.telenav.cactus.maven.util.ProcessResultConverter;
import com.telenav.cactus.maven.util.ThrowingOptional;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
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

    public static final GitCommand<String> GET_BRANCH
            = new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                    "rev-parse", "--abbrev-ref", "HEAD");

    public static final GitCommand<Boolean> NO_MODIFICATIONS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().trueIfEmpty(),
                    "status", "--porcelain");

    public static final GitCommand<Map<String, GitRemotes>> LIST_REMOTES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(GitRemotes::from),
                    "remote", "-v");

    public static final GitCommand<Branches> ALL_BRANCHES
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().map(Branches::from),
                    "branch", "--no-color", "-a");

    public static final GitCommand<Boolean> IS_DIRTY
            = new GitCommand<>(ProcessResultConverter.exitCode(code -> code != 0),
                    "diff", "--quiet");

    public static final GitCommand<Boolean> IS_DETACHED_HEAD
            = new GitCommand<>(ProcessResultConverter.strings().testedWith(
                    text -> text.contains("(detached)")),
                    "status", "--porcelain=2", "--branch");

    private final GitCommand<List<SubmoduleStatus>> listSubmodules
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(this::parseSubmoduleInfo), "submodule", "status");

    private final Path root;

    GitCheckout(Path root)
    {
        this.root = root;
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

    public Collection<? extends GitRemotes> allRemotes()
    {
        return LIST_REMOTES.withWorkingDir(root).run().awaitQuietly().values();
    }

    public String branch()
    {
        return GET_BRANCH.withWorkingDir(root).run().awaitQuietly();
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
        try ( Stream<Path> str = Files.walk(root).filter(isSameRoot))
        {
            str.forEach(path ->
            {
                Path pom = path.resolve("pom.xml");
                if (Files.exists(pom))
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
            return submoduleRoot().flatMapThrowing((GitCheckout rootRepo) -> rootRepo.submodules());
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
}
