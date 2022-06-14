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

package com.telenav.cactus.maven.sourceanalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class SourcesScanner
{

    private final SourceScorer scorer;

    public SourcesScanner()
    {
        this(new WordCount());
    }

    public SourcesScanner(SourceScorer scorer)
    {
        this.scorer = scorer;
    }

    public int scan(Path sourceRoot, BiConsumer<Path, Integer> output) throws IOException
    {
        int result = 0;
        try ( Stream<Path> files = Files.walk(sourceRoot).filter(
                SourcesScanner::isJavaFile))
        {
            for (Path path : files.collect(Collectors.toCollection(
                    ArrayList::new)))
            {
                int score = scorer.score(path);
                output.accept(path, score);
                result++;
            }
        }
        return result;
    }

    private static boolean isJavaFile(Path path)
    {
        return path.getFileName().toString().endsWith(".java")
                && !Files.isDirectory(path)
                && Files.isReadable(path);
    }
}
