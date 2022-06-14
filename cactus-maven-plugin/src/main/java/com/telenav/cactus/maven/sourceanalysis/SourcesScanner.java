package com.telenav.cactus.maven.sourceanalysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 * @author Tim Boudreau
 */
public class SourcesScanner {

    private final SourceScorer scorer;

    public SourcesScanner() {
        this(new WordCount());
    }

    public SourcesScanner(SourceScorer scorer) {
        this.scorer = scorer;
    }

    public int scan(Path sourceRoot, BiConsumer<Path, Integer> output) throws IOException {
        int result = 0;
        try ( Stream<Path> files = Files.walk(sourceRoot).filter(SourcesScanner::isJavaFile)) {
            for (Path path : files.collect(Collectors.toCollection(ArrayList::new))) {
                int score = scorer.score(path);
                output.accept(path, score);
                result++;
            }
        }
        return result;
    }

    private static boolean isJavaFile(Path path) {
        return path.getFileName().toString().endsWith(".java")
                && !Files.isDirectory(path)
                && Files.isReadable(path);
    }

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("/Users/timb/work/telenav/jonstuff/kivakit/kivakit-core/src/main/java");
        new SourcesScanner().scan(p, (path, ct) -> {
            System.out.println(ct + "\t" + path.getFileName() + "\t" + Thread.currentThread().getName());
        });
    }
}
