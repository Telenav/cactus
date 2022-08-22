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
import java.util.Optional;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A trivial interface for reading typed values from a key/value pair set of
 * preferences. These should be used sparingly and only where there is a real
 * need.
 * <p>
 * Key names are always lower case, with _ replaced with - from the name() value
 * of a passed preference, to facilitate using Enum types as Preference
 * implementations.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class CactusPreferences
{
    private static final String CACTUS_SETTINGS_SYSTEM_PROPERTY = "cactus.settings";
    private static final String CACTUS_SETTINGS_ENV_VAR = "CACTUS_SETTINGS";
    private static final String CACTUS_SETTINGS_RELATIVE_PATH = "cactus/cactus.properties";
    public static final String DEFAULT_VALUE_MARKER = "_";

    private static CactusPreferences instance;
    private final PreferencesFile file;

    private CactusPreferences(PreferencesFile file)
    {
        this.file = file;
    }

    private static CactusPreferences cactusPreferences()
    {
        return instance == null
               ? instance = load()
               : instance;
    }

    private static CactusPreferences load()
    {
        Optional<Path> file = preferencesFile();
        return new CactusPreferences(file.<PreferencesFile>map(
                path -> new PropertiesPreferences(path)).orElse(
                        PreferencesFile.NONE));
    }

    private static Optional<Path> preferencesFile()
    {
        return PathSupplier.fromEnvironment(CACTUS_SETTINGS_ENV_VAR)
                .ifReadableFile()
                .or(PathSupplier.fromSystemProperty(
                        CACTUS_SETTINGS_SYSTEM_PROPERTY).ifReadableFile())
                .or(PathSupplier.fromSettingsDir(CACTUS_SETTINGS_RELATIVE_PATH)
                        .ifReadableFile())
                .get();
    }

    public static <T, E extends Enum<E> & Preference<T>> T get(E arg)
    {
        return cactusPreferences().read(arg);
    }

    private <T, E extends Enum<E> & Preference<T>> T read(E arg)
    {
        String preferenceName = notNull("arg.toString()", notNull("arg", arg)
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
