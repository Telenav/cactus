package com.telenav.cactus.maven.sourceanalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 *
 * @author Tim Boudreau
 */
public interface SourceScorer
{

    int score(Path path) throws IOException;

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
