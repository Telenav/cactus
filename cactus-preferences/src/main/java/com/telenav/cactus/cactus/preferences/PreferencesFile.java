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

import java.util.Optional;

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
