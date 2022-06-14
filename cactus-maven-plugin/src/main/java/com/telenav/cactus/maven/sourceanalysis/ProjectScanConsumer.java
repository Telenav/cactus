package com.telenav.cactus.maven.sourceanalysis;

import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public interface ProjectScanConsumer
{

    void onProjectScanned(Pom pom, Map<Path, Integer> scores) throws IOException;

    default void onDone() throws IOException
    {

    }
}
