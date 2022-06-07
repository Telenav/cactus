package com.telenav.cactus.maven.util;

import static com.mastfrog.util.preconditions.Checks.notNull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class PathUtils
{

    private static final Map<String, Optional<Path>> BINARY_PATH_CACHE = new ConcurrentHashMap<>();

    public static Path home()
    {
        return fromSystemProperty("user.home", () -> fromSystemProperty("java.io.tmpdir", () -> Paths.get("/")));
    }

    private static Path fromSystemProperty(String what, Supplier<Path> fallback)
    {
        String prop = System.getProperty(what);
        return prop == null ? fallback.get() : Paths.get(prop);
    }

    /**
     * Returns a shorter string prefixed with ~/ if the passed path is below the
     * user's home dir.
     *
     * @param what A path
     * @return A string representation of the path that shortens it if it can.
     */
    public static String homeRelativePath(Path what)
    {
        Path home = home();
        if (what.startsWith(home))
        {
            return "~/" + home.relativize(what).toString();
        }
        return what.toString();
    }

    public static Optional<Path> findExecutable(String name, Path... additionalSearchLocations)
    {
        if (additionalSearchLocations.length == 0)
        {
            return BINARY_PATH_CACHE.computeIfAbsent(name, n ->
            {
                return _findExecutable(n);
            });
        }
        return _findExecutable(name, additionalSearchLocations);
    }

    private static Optional<Path> _findExecutable(String name, Path... additionalSearchLocations)
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
        Path home = home();
        // Ensure we look in some common places:
        all.addAll(Arrays.asList(Paths.get("/bin"),
                Paths.get("/usr/bin"),
                Paths.get("/usr/local/bin"),
                Paths.get("/opt/bin"),
                Paths.get("/opt/local/bin"),
                Paths.get("/opt/homebrew/bin"),
                home.resolve(".local").resolve("bin"),
                home.resolve("bin")
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

    public static Path userCacheRoot()
    {
        Path home = Paths.get(System.getProperty("user.home", System.getenv("HOME")));
        String os = System.getProperty("os.name");
        if ("Mac OS X".equals(os))
        {
            return home.resolve("Library").resolve("Caches");
        } else
        {
            // Linux default; Windows would be ? Do we care?
            return home.resolve(".cache");
        }
    }

    public static Path temp()
    {
        return Paths.get(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Delete a folder and its subtree; handles the condition that this method
     * may race and files may already have been deleted (for example, shutting
     * down a database process which deletes its pidfile) after the set of files
     * being iterated was computed, silently.
     *
     * @param dir A directory
     * @throws IOException If a file cannot be deleted, the passed file is not a
     * directory, or the process owner does not have the needed permissions
     */
    public static void deleteWithSubtree(Path dir) throws IOException
    {
        // borrowed from com.mastfrog.util.streams.Streams
        if (!Files.exists(dir))
        {
            return;
        }
        if (!Files.isDirectory(dir))
        {
            throw new IOException("Not a directory: " + dir);
        }
        Set<Path> paths = new HashSet<>();
        for (;;)
        {
            try ( Stream<Path> all = Files.walk(dir))
            {
                all.forEach(paths::add);
                break;
            } catch (NoSuchFileException ex)
            {
                // ok, pid file deleted by postgres during
                // shutdown or similar racing with our file deletion
            }
        }
        List<Path> l = new ArrayList<>(paths);
        l.sort((pa, pb) ->
        {
            return -Integer.compare(pa.getNameCount(), pb.getNameCount());
        });
        for (Path p : l)
        {
            try
            {
                Files.delete(p);
            } catch (NoSuchFileException ex)
            {
                // do nothing - this can race, since a process may still be
                // shutting down and deleting things
            }
        }
    }

    private PathUtils()
    {
        throw new AssertionError();
    }
}
