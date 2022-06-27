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
package com.telenav.cactus.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads a file and generates an integer score for it.
 *
 * @author Tim Boudreau
 */
public interface SourceScorer
{

    /**
     * Score one file.
     *
     * @param path A file
     * @return A score
     * @throws IOException if something goes wrong
     */
    int score(Path path) throws IOException;

    /**
     * Convenience scorer implementation that processes the file's content as a
     * string.
     */
    public interface StringSourceScorer extends SourceScorer
    {

        @Override
        default int score(Path path) throws IOException
        {
            return score(path, Files.readString(path));
        }

        int score(Path path, String lines);
    }
}
