package com.telenav.cactus.maven.sourceanalysis;

import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class CodeflowersJsonGenerator implements ProjectScanConsumer {

    private final Path outputDir;
    
    CodeflowersJsonGenerator(Path outputDir) {
        this.outputDir = outputDir;
    }

    @Override
    public void onProjectScanned(Pom pom, Map<Path, Integer> scores) throws IOException {
        
    }

}
