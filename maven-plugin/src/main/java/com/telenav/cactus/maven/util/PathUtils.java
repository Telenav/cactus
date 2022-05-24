package com.telenav.cactus.maven.util;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 * @author Tim Boudreau
 */
public class PathUtils
{

    public static Optional<Path> findExecutable(String name, Path... additionalSearchLocations)
    {
        if (name.indexOf(File.separatorChar) >= 0)
        {
            Path path = Paths.get(name);
            if (Files.exists(path) && Files.isExecutable(path))
            {
                return Optional.of(path);
            }
            name = path.getFileName().toString();
        }
        String systemPath = System.getenv("PATH");
        Set<Path> all = new LinkedHashSet<>(Arrays.asList(additionalSearchLocations));
        if (systemPath != null)
        {
            for (String s : systemPath.split(":"))
            {
                all.add(Paths.get(s));
            }
        }
        // Ensure we look in some common places:
        all.addAll(Arrays.asList(Paths.get("/bin"), Paths.get("/usr/bin"),
                Paths.get("/usr/local/bin"), Paths.get("/opt/bin"), Paths.get("/opt/local/bin"),
                Paths.get("/opt/homebrew/bin"),
                Paths.get(System.getProperty("user.home")).resolve(".local").resolve("bin"),
                Paths.get(System.getProperty("user.home")).resolve("bin")
        ));
        return findExecutable(all, name);
    }

    public static Optional<Path> findExecutable(Iterable<? extends Path> in, String name)
    {
        for (Path path : in)
        {
            Path target = path.resolve(name);
            if (Files.exists(target) && Files.isExecutable(target))
            {
                return Optional.of(target);
            }
        }
        return Optional.empty();
    }

    public static Optional<Path> findGitCheckoutRoot(Path of, boolean skipSubmodules)
    {
        // If .git is a file, we are in a submodule
        return findParentWithChild(of, skipSubmodules
                ? FileKind.FOLDER : FileKind.EITHER, ".git");
    }

    public static Optional<Path> findParentWithChild(Path of, FileKind dirOrFile, String fileOrFolderName)
    {
        return findInParents(of, path ->
        {
            Path child = path.resolve(fileOrFolderName);
            return dirOrFile.test(child);
        });
    }

    public enum FileKind implements Predicate<Path>
    {
        FILE,
        FOLDER,
        EITHER;

        @Override
        public boolean test(Path t)
        {
            boolean result = Files.exists(t);
            if (result)
            {
                switch (this)
                {
                    case FILE:
                        result = !Files.isDirectory(t);
                        break;
                    case FOLDER:
                        result = Files.isDirectory(t);
                        break;
                    default:
                    // do nothing
                }
            }
            return result;
        }
    }

    public static Optional<Path> findInParents(Path of, Predicate<Path> test)
    {
        if (!Files.isDirectory(notNull("of", of)))
        {
            of = of.getParent();
        }
        while (of != null)
        {
            if (test.test(of))
            {
                return Optional.of(of);
            }
            of = of.getParent();
        }
        return Optional.empty();
    }

    private PathUtils()
    {
        throw new AssertionError();
    }
}
