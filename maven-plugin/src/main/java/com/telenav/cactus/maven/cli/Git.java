package com.telenav.cactus.maven.cli;

import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.util.PathUtils;
import com.telenav.cactus.maven.util.ProcessResultConverter;
import com.telenav.cactus.maven.util.ThrowingOptional;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 *
 *
 * @author Tim Boudreau
 */
public final class Git
{

    public static final GitCommand<String> GET_BRANCH
            = new GitCommand<>(ProcessResultConverter.strings().trimmed(),
                    "rev-parse", "--abbrev-ref", "HEAD");

    public static final GitCommand<Boolean> NO_MODIFICATIONS
            = new GitCommand<>(ProcessResultConverter.strings().trimmed().trueIfEmpty(),
                    "status", "--porcelain");

    private final GitCommand<List<SubmoduleStatus>> listSubmodules
            = new GitCommand<>(ProcessResultConverter.strings().trimmed()
                    .map(this::parseSubmoduleInfo), "submodule", "status");

    private final Path root;

    Git(Path root)
    {
        this.root = root;
    }

    public static Optional<Git> repository(File dir)
    {
        return repository(dir.toPath());
    }

    public static Optional<Git> repository(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(Git::new);
    }

    public static Optional<Git> submodulesRoot(Path dirOrFile)
    {
        return PathUtils.findGitCheckoutRoot(dirOrFile, false)
                .map(Git::new);
    }

    public String branch() throws InterruptedException
    {
        return GET_BRANCH.withWorkingDir(root).run().await();
    }

    public boolean hasUncommitedChanges() throws InterruptedException
    {
        return !NO_MODIFICATIONS.withWorkingDir(root).run().await();
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

    public ThrowingOptional<Git> submoduleRoot()
    {
        return ThrowingOptional.from(PathUtils.findGitCheckoutRoot(root, true))
                .map((Path dir) ->
                {
                    if (dir.equals(root))
                    {
                        return this;
                    }
                    return new Git(dir);
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
        } else if (o == null || o.getClass() != Git.class)
        {
            return false;
        }
        return ((Git) o).checkoutRoot().equals(checkoutRoot());
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

    public ThrowingOptional<List<SubmoduleStatus>> submodules() throws InterruptedException
    {
        if (isSubmoduleRoot())
        {
            List<SubmoduleStatus> infos = listSubmodules
                    .withWorkingDir(root)
                    .run()
                    .await();
            return infos.isEmpty() ? ThrowingOptional.empty() : ThrowingOptional.of(infos);
        } else
        {
            return submoduleRoot().flatMapThrowing(
                    (Git rootRepo) -> rootRepo.submodules());
        }
    }

    List<SubmoduleStatus> parseSubmoduleInfo(String output)
    {
        return SubmoduleStatus.fromStatusOutput(root, output);
    }
}
