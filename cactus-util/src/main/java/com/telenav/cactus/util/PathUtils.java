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
package com.telenav.cactus.util;

import com.mastfrog.function.state.Int;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.maven.log.BuildLog;

import static com.mastfrog.util.preconditions.Checks.notNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.System.getenv;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Optional.ofNullable;

/**
 *
 * @author Tim Boudreau
 */
public class PathUtils
{

    private static final Map<String, Optional<Path>> BINARY_PATH_CACHE = new ConcurrentHashMap<>();

    public static Path home()
    {
        return fromSystemProperty("user.home", () -> fromSystemProperty(
                "java.io.tmpdir", () -> Paths.get(".")));
    }

    public static Path fromSystemProperty(String what, Supplier<Path> fallback)
    {
        String prop = System.getProperty(what);
        return prop == null
               ? fallback.get()
               : Paths.get(prop);
    }

    public static Path fromEnvironment(String what, Supplier<Path> fallback)
    {
        String prop = getenv(what);
        return prop == null
               ? fallback.get()
               : Paths.get(prop);
    }

    public static Optional<Path> ifExists(Path path)
    {
        if (path == null)
        {
            return Optional.empty();
        }
        if (Files.exists(path))
        {
            return Optional.of(path);
        }
        return Optional.empty();
    }

    public static Optional<Path> ifDirectory(Path path)
    {
        return ifExists(path).flatMap(maybeDir ->
        {
            return Files.isDirectory(maybeDir)
                   ? Optional.of(maybeDir)
                   : Optional.empty();
        });
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

    /**
     * Find an executable on the default system path one of the passed set of
     * paths.
     *
     * @param name The name of the executable
     * @param additionalSearchLocations More locations to search
     * @return A path if possible
     */
    public static Optional<Path> findExecutable(String name,
            Path... additionalSearchLocations)
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

    private static Optional<Path> _findExecutable(String name,
            Path... additionalSearchLocations)
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
        Set<Path> all = new LinkedHashSet<>(Arrays.asList(
                additionalSearchLocations));
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

    public static Optional<Path> findExecutable(Iterable<? extends Path> in,
            String name)
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

    public static Optional<Path> findGitCheckoutRoot(Path of,
            boolean skipSubmodules)
    {
        // If .git is a file, we are in a submodule
        return findParentWithChild(of, skipSubmodules
                                       ? FileKind.FOLDER
                                       : FileKind.EITHER, ".git");
    }

    /**
     * Allows pattern based matching of parent folders which contain some file,
     * such as seeking the .git directory in directories below a starting
     * folder.
     *
     * @param of A starting folder
     * @param dirOrFile Whether the file looked for is a directory or a file or
     * can be either
     * @param fileOrFolderName The name of the child file which must be present
     * in the ancestor folder
     * @return A folder, if possible
     */
    public static Optional<Path> findParentWithChild(Path of, FileKind dirOrFile,
            String fileOrFolderName)
    {
        return findInParents(of, path ->
        {
            Path child = path.resolve(fileOrFolderName);
            return dirOrFile.test(child);
        });
    }

    /**
     * Kinds of files search for with <code>findParentWithChild</code>.
     */
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

    /**
     * Find an ancestor folder of the passed folder which matches the passed
     * predicate.
     *
     * @param of A folder which, along with its parent folders, should be tested
     * @param test The test to apply
     * @return The passed folder or its first ancestor that passes the test
     */
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

    /**
     * Get the cache directory in the user's home directory - on linux,
     * ~/.cache, on Mac OS X ~/Library/Caches
     *
     * @return A Path
     */
    public static Path userCacheRoot()
    {
        Path home = home();
        String os = System.getProperty("os.name");
        if ("Mac OS X".equals(os))
        {
            return home.resolve("Library").resolve("Caches");
        }
        else
        {
            // Linux default; Windows would be ? Do we care?
            return home.resolve(".cache");
        }
    }

    public static Path userSettingsRoot()
    {
        Path home = home();
        String os = System.getProperty("os.name");
        if ("Mac OS X".equals(os))
        {
            return home.resolve("Library").resolve("Application Support");
        }
        else
        {
            // Linux default; Windows would be ? Do we care?
            return home.resolve(".config");
        }
    }

    /**
     * Get the system temporary directory.
     *
     * @return A path
     */
    public static Path temp()
    {
        return Paths.get(System.getProperty("java.io.tmpdir", "/tmp"));
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
            }
            catch (NoSuchFileException ex)
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
            }
            catch (NoSuchFileException ex)
            {
                // do nothing - this can race, since a process may still be
                // shutting down and deleting things
            }
        }
    }

    public static int deleteFolderTree(Path path) throws IOException
    {
        if (path == null || !Files.exists(path))
        {
            return 0;
        }
        List<Path> all = Files.walk(path, 1000).collect(Collectors.toCollection(
                ArrayList::new));
        Collections.sort(all, (a, b) ->
        {
            return Integer.compare(b.getNameCount(), a.getNameCount());
        });
        int result = 0;
        for (Path fileOrFolder : all)
        {
            if (Files.deleteIfExists(fileOrFolder))
            {
                result++;
            }
        }
        if (Files.deleteIfExists(path))
        {
            result++;
        }
        return result;
    }

    /**
     * Copy an entire folder tree.
     *
     * @param log A log
     * @param from The source folder
     * @param to The target folder
     * @return a 2-element array with the number of files copied and the number
     * of folders created
     * @throws IOException if something goes wrong
     */
    public static int[] copyFolderTree(BuildLog log, Path from, Path to) throws IOException
    {
        Int files = Int.create();
        Int dirs = Int.create();
        try ( Stream<Path> srcStream = Files.walk(from, 1280))
        {
            srcStream.forEach(fileOrDir ->
            {
                Path rel = from.relativize(fileOrDir);
                Path target = to.resolve(rel);
                boolean dir = Files.isDirectory(fileOrDir);
                quietly(() ->
                {
                    Path destDir = dir
                                   ? target
                                   : target.getParent();
                    if (!Files.exists(destDir))
                    {
                        Files.createDirectories(destDir);
                        dirs.increment();
                    }
                    if (!dir)
                    {
                        if (!Files.exists(fileOrDir))
                        {
                            Files.copy(fileOrDir, target);
                        }
                        else
                        {
                            Files.copy(fileOrDir, target, REPLACE_EXISTING);
                        }
                        files.increment();
                    }
                });
            });
        }
        return new int[]
        {
            files.getAsInt(), dirs.getAsInt()
        };
    }

    /**
     * Unzip the passed input stream into the passed directory.
     *
     * @param in An input stream
     * @param dir A folder
     * @throws IOException if something goes wrong
     */
    public static void unzip(InputStream in, Path dir) throws IOException
    {
        notNull("dir", dir);
        try ( ZipInputStream zip = new ZipInputStream(notNull("in", in)))
        {
            ZipEntry en;
            while ((en = zip.getNextEntry()) != null)
            {
                if (en.isDirectory())
                {
                    Path dest = dir.resolve(en.getName());
                    if (!Files.exists(dest))
                    {
                        Files.createDirectories(dest);
                    }
                }
                else
                {
                    Path dest = dir.resolve(en.getName());
                    if (!Files.exists(dest.getParent()))
                    {
                        Files.createDirectories(dest.getParent());
                    }
                    Files.copy(zip, dest,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void quietly(ThrowingRunnable tr)
    {
        tr.toNonThrowing().run();
    }

    private PathUtils()
    {
        throw new AssertionError();
    }
}
