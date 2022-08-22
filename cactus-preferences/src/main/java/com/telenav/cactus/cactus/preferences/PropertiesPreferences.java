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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import static java.nio.file.StandardOpenOption.READ;
import static java.util.Optional.ofNullable;

/**
 *
 * @author Tim Boudreau
 */
final class PropertiesPreferences implements PreferencesFile
{

    private final Properties props = new Properties();

    PropertiesPreferences(Path file)
    {
        try ( InputStream in = Files.newInputStream(file, READ))
        {
            props.load(in);
        }
        catch (IOException ex)
        {
            ex.printStackTrace(System.err);
        }
    }

    @Override
    public Optional<String> read(String key)
    {
        return ofNullable(props.getProperty(key));
    }

}
