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

import com.mastfrog.function.state.Obj;
import com.telenav.cactus.util.PathSupplier;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Supplier;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.util.PathSupplier.existingFileOrInParents;
import static com.telenav.cactus.util.PathSupplier.fromEnvironment;
import static com.telenav.cactus.util.PathSupplier.fromSettingsDir;
import static com.telenav.cactus.util.PathSupplier.fromSystemProperty;
import static java.util.Collections.synchronizedMap;

/**
 * A trivial interface for reading typed values from a key/value pair set of
 * preferences. These should be used sparingly and only where there is a real
 * need.
 * <p>
 * Key names are always lower case, with _ replaced with - from the name() value
 * of a passed preference, to facilitate using Enum types as Preference
 * implementations.
 * </p>
 * Properties are resolved from a layered
 *
 * @author Tim Boudreau
 */
public final class CactusPreferences
{
    private static final String CACTUS_SETTINGS_SYSTEM_PROPERTY = "cactus.settings";
    private static final String CACTUS_SETTINGS_ENV_VAR = "CACTUS_SETTINGS";
    private static final String CACTUS_SETTINGS_RELATIVE_PATH = "cactus/cactus.properties";
    private static final String LOCAL_PROPERTIES = ".cactus.properties";
    public static final String DEFAULT_VALUE_MARKER = "_";

    private static final Map<Path, Reference<CactusPreferences>> CACHE
            = synchronizedMap(new HashMap<>());
    private final PreferencesFile file;

    private CactusPreferences(PreferencesFile file)
    {
        this.file = file;
    }

    public static CactusPreferences cactusPreferences(Path dir)
    {
        return cactusPreferences(dir, () -> null);
    }

    public static CactusPreferences cactusPreferences(Path dir,
            Supplier<Properties> initial)
    {
        Obj<CactusPreferences> strongReference = Obj.create();
        CACHE.compute(notNull("dir", dir), (d, ref) ->
        {
            CactusPreferences prefs;
            if (ref == null)
            {
                prefs = _cactusPreferences(d, initial);
                strongReference.set(prefs);
                return new WeakReference<>(prefs);
            }
            else
            {
                prefs = ref.get();
                if (prefs == null)
                {
                    prefs = _cactusPreferences(d, initial);
                    strongReference.set(prefs);
                    return new WeakReference<>(prefs);
                }
                return ref;
            }
        });
        return strongReference.get();
    }

    private static CactusPreferences _cactusPreferences(Path dir,
            Supplier<Properties> initial)
    {
        Properties props = initial.get();
        PreferencesFile first = props == null || props.isEmpty()
                                ? PreferencesFile.NONE
                                : new PropertiesPreferences(props);
        return new CactusPreferences(first.or(findAll(dir)));
    }

    private static PreferencesFile findAll(Path startingDir)
    {
        return PreferencesFile.mapMany(
                existingFileOrInParents(startingDir, LOCAL_PROPERTIES),
                fromSystemProperty(CACTUS_SETTINGS_SYSTEM_PROPERTY)
                        .ifReadableFile(),
                fromEnvironment(CACTUS_SETTINGS_ENV_VAR).ifReadableFile(),
                fromSettingsDir(
                        CACTUS_SETTINGS_RELATIVE_PATH)
                        .ifReadableFile()
        );
    }

    private static Optional<Path> preferencesFile(Path p)
    {
        return PathSupplier
                .existingFileOrInParents(p, LOCAL_PROPERTIES)
                .or(PathSupplier
                        .fromEnvironment(CACTUS_SETTINGS_ENV_VAR)
                        .ifReadableFile()
                        .or(PathSupplier.fromSystemProperty(
                                CACTUS_SETTINGS_SYSTEM_PROPERTY)
                                .ifReadableFile())
                        .or(PathSupplier.fromSettingsDir(
                                CACTUS_SETTINGS_RELATIVE_PATH)
                                .ifReadableFile()))
                .get();
    }

    public <T, E extends Preference<T>> T get(E arg)
    {
        String preferenceName = notNull("arg.name()", notNull("arg", arg)
                .name()).toLowerCase().replace('_', '-');
        Optional<String> fileValue = file.read(preferenceName);
        if (fileValue.isPresent())
        {
            String val = fileValue.get();
            switch (val)
            {
                case DEFAULT_VALUE_MARKER:
                    return arg.defaultValue();
                default:
                    T result = arg.interpret(val);
                    if (result == null)
                    {
                        return arg.defaultValue();
                    }
                    return result;
            }
        }
        else
        {
            return arg.defaultValue();
        }
    }
}
