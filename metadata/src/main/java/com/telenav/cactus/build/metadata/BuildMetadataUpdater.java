////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2021 Telenav, Inc.
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

package com.telenav.cactus.build.metadata;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * This application is run from Maven builds to produce a <i>build.properties</i> file in <i>src/main/java</i>
 * containing the build number, date and name.
 *
 * @author jonathanl (shibo)
 * @see BuildMetadata
 */
public class BuildMetadataUpdater
{
    /**
     * Writes a build.properties file out to the given output folder with the following entries:
     *
     * <ul>
     *     <li>build-number - The current build number since the start of the KivaKit epoch</li>
     *     <li>build-date - The current build date as [year].[month].[day-of-month]</li>
     *     <li>build-name - The current build name</li>
     * </ul>
     *
     * <p>
     * Some KivaKit scripts read this information, as well as kivakit-core-kernel.
     * </p>
     *
     * @param arguments Output folder to write metadata to
     */
    public static void main(final String[] arguments)
    {
        if (arguments.length == 1)
        {
            try
            {
                // Get output path and ensure it exists,
                final var outputPath = Path.of(arguments[0]);
                if (!Files.isDirectory(outputPath))
                {
                    Files.createDirectory(outputPath);
                }

                // formulate the lines of the build.properties file,
                final var properties = new BuildMetadata(null, BuildMetadata.Type.CURRENT).buildProperties();
                final var lines = new ArrayList<String>();
                for (final var key : properties.keySet())
                {
                    lines.add(key + " = " + properties.get(key));
                }

                // and write them to the output folder.
                try (final var out = new PrintStream(outputPath.resolve("build.properties").toFile()))
                {
                    out.println(String.join("\n", lines));
                }
            }
            catch (final Exception cause)
            {
                throw new IllegalStateException("Unable to write metadata", cause);
            }
        }
        else
        {
            System.err.println("Usage: kivakit-metadata [output-folder]");
        }
    }
}
