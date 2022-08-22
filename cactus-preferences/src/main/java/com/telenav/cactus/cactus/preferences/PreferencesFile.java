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
package com.telenav.cactus.cactus.preferences;

import com.telenav.cactus.util.PathSupplier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;

/**
 *
 * @author Tim Boudreau
 */
interface PreferencesFile
{

    static final PreferencesFile NONE = key -> empty();

    Optional<String> read(String key);

    static PreferencesFile fromPathSupplier(PathSupplier supp)
    {
        return supp.get().<PreferencesFile>map(PropertiesPreferences::new)
                .orElse(NONE);
    }

    static PreferencesFile mapMany(PathSupplier... all)
    {
        return PathSupplier.mapMany((paths ->
        {
            return aggregate(fromPathSuppliers(paths));
        }), all).get();
    }

    static Collection<? extends PreferencesFile> fromPathSuppliers(
            Collection<? extends Path> all)
    {
        List<PreferencesFile> files = new ArrayList<>();
        for (Path path : all)
        {
            files.add(new PropertiesPreferences(path));
        }
        return files;
    }

    static PreferencesFile aggregate(Collection<? extends PreferencesFile> files)
    {
        PreferencesFile result = NONE;
        for (PreferencesFile p : files)
        {
            if (p == NONE)
            {
                continue;
            }
            if (result == NONE)
            {
                result = p;
            }
            else
            {
                result = result.or(p);
            }
        }
        return result;
    }

    default PreferencesFile or(PreferencesFile other)
    {
        if (other == null || other == NONE)
        {
            return this;
        }
        else
            if (this == NONE)
            {
                return other;
            }
            else
                if (other == this)
                {
                    throw new IllegalArgumentException();
                }
        return k ->
        {
            return read(k).or(() -> other.read(k));
        };
    }

    default PreferencesFile overridingPreference(String key, String value)
    {
        return k ->
        {
            if (key.equals(k))
            {
                return ofNullable(value);
            }
            return read(k);
        };
    }
}
