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

import com.mastfrog.function.throwing.ThrowingFunction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.lang.System.getProperty;
import static java.lang.System.getenv;
import static java.nio.file.Files.isReadable;
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

/**
 * Supplier of Paths with convenience methods for fallbacks and determining
 * status.
 *
 * @author Tim Boudreau
 */
public interface PathSupplier extends Supplier<Optional<Path>>
{
    
    public static PathSupplier NONE = () -> Optional.empty();

    default <T> Supplier<Optional<T>> map(ThrowingFunction<Path, T> f)
    {
        return () ->
        {
            return get().map(f.toNonThrowing());
        };
    }

    static <T> Supplier<T> mapMany(
            Function<Collection<? extends Path>, T> converter,
            PathSupplier... suppliers)
    {
        return () ->
        {
            List<Path> paths = new ArrayList<>(suppliers.length);
            for (PathSupplier p : suppliers)
            {
                p.get().ifPresent(paths::add);
            }
            return converter.apply(paths);
        };
    }

    static PathSupplier existingFileOrInParents(Path start, String path)
    {
        notNull("start", start);
        notNull("path", path);
        return () ->
        {
            Path dir = start;
            while (dir != null)
            {
                Path res = dir.resolve(path);
                if (Files.exists(res))
                {
                    return Optional.of(res);
                }
                dir = dir.getParent();
            }
            return empty();
        };
    }

    static PathSupplier fromWorkingDirOrParents(String path)
    {
        return () ->
        {
            Path dir = Paths.get(".");
            while (dir != null)
            {
                Path res = dir.resolve(path);
                if (Files.exists(res))
                {
                    return Optional.of(res);
                }
                dir = dir.getParent();
            }
            return empty();
        };
    }

    static PathSupplier fromEnvironment(String envVar)
    {
        notNull("envVar", envVar);
        return () ->
        {
            String result = getenv(envVar);
            if (result == null)
            {
                return empty();
            }
            return Optional.of(Paths.get(result));
        };
    }

    static PathSupplier fromSystemProperty(String sysprop)
    {
        notNull("sysprop", sysprop);
        return () ->
        {
            String result = getProperty(sysprop);
            if (result == null)
            {
                return empty();
            }
            return Optional.of(Paths.get(result));
        };
    }

    static PathSupplier fromWorkingDir(String relativePath)
    {
        return () ->
        {
            return Optional.of(Paths.get(".").resolve(relativePath));
        };
    }

    static PathSupplier fromCacheDir(String relativePath)
    {
        return () ->
        {
            return Optional.of(PathUtils.userCacheRoot().resolve(relativePath));
        };
    }

    static PathSupplier fromSettingsDir(String relativePath)
    {
        return () ->
        {
            return Optional.of(PathUtils.userSettingsRoot()
                    .resolve(relativePath));
        };
    }

    static PathSupplier of(Path path)
    {
        return () -> ofNullable(path);
    }

    default PathSupplier or(PathSupplier other)
    {
        return () ->
        {
            return get().or(other);
        };
    }

    default PathSupplier resolving(Path subpath)
    {
        return () -> get().map(p -> p.resolve(subpath));
    }

    default PathSupplier ifDirectory()
    {
        return () ->
        {
            Optional<Path> result = get();
            if (result.isPresent())
            {
                Path p = result.get();
                if (!Files.isDirectory(p))
                {
                    return empty();
                }
            }
            return result;
        };

    }

    default PathSupplier ifReadableFile()
    {
        return () ->
        {
            Optional<Path> result = get();
            if (result.isPresent())
            {
                Path p = result.get();
                if (Files.isDirectory(p) || !isReadable(p))
                {
                    return empty();
                }
            }
            return result;
        };
    }

    default PathSupplier ifExists()
    {
        return () ->
        {
            Optional<Path> result = get();
            if (result.isPresent())
            {
                Path p = result.get();
                if (!Files.exists(p))
                {
                    return empty();
                }
            }
            return result;
        };
    }

    default PathSupplier memoizing()
    {
        return new PathSupplier()
        {
            Optional<Path> result;

            @Override
            public Optional<Path> get()
            {
                return result == null
                       ? result = PathSupplier.this.get()
                       : result;
            }

        };
    }

}
