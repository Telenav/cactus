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

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author Tim Boudreau
 */
public final class MavenProjectsScanner
{
    private final ConcurrentLinkedList<Pom> poms;
    private final BuildLog log;
    private final SourcesScanner scanner;
    private Function<Path, Path> sourceDirFinder;
    private boolean verbose;

    public MavenProjectsScanner(BuildLog log, SourceScorer scorer,
            Collection<? extends Pom> poms, boolean verbose)
    {
        this(log, scorer, poms, MavenProjectsScanner::defaultSourceDir, verbose);
        this.verbose = verbose;
    }

    public MavenProjectsScanner(BuildLog log, SourceScorer scorer,
            Collection<? extends Pom> poms, Function<Path, Path> sourceDirFinder,
            boolean verbose)
    {
        this.poms = ConcurrentLinkedList.lifo();
        this.scanner = new SourcesScanner(notNull("scorer", scorer));
        this.sourceDirFinder = notNull("sourceDirFinder", sourceDirFinder);
        this.verbose = verbose;
        // Use a biconsumer for manual testing without SLF4J on the classpath
        this.log = log.child(getClass().getSimpleName());
        if (verbose)
        {
            this.log.info(
                    "Was passed " + poms.size() + " projects to scan: " + poms);
        }
        poms.stream()
                .filter(pom -> (!pom.isPomProject()))
                .forEach(this.poms::push);
    }

    public void scan(ProjectScanConsumer c) throws InterruptedException, IOException
    {
        if (verbose)
        {
            log.info("Scan " + poms.size() + " projects using " + c);
        }
        int count = Runtime.getRuntime().availableProcessors();
        CountDownLatch latch = new CountDownLatch(count);
        Set<Pom> scanned = ConcurrentHashMap.newKeySet();
        for (int i = 0; i < count - 1; i++)
        {
            ForkJoinPool.commonPool().submit(() -> scanned.addAll(
                    scanLoop(latch, c)));
        }
        scanned.addAll(scanLoop(latch, c));
        latch.await();
        c.onDone();
        if (verbose)
        {
            log.info("Scanned " + scanned.size() + " projects:");
            scanned.forEach(pom -> log.info("  * " + pom.artifactId()));
        }
    }

    private Set<Pom> scanLoop(CountDownLatch latch, ProjectScanConsumer c)
    {
        Set<Pom> result = new HashSet<>();
        try
        {
            Pom pom;
            while ((pom = poms.pop()) != null)
            {
                scanOne(pom, c);
                result.add(pom);
            }
        }
        finally
        {
            latch.countDown();
        }
        return result;
    }

    private void scanOne(Pom pom, ProjectScanConsumer c)
    {
        try
        {
            Map<Path, Integer> scoreForSourceFileRelativePath = performScan(pom);
            if (scoreForSourceFileRelativePath != null)
            {
                c.onProjectScanned(pom, scoreForSourceFileRelativePath);
            }
            else
            {
                log.warn("Could not scan source dir for " + pom);
            }
        }
        catch (Exception | Error ex)
        {
            log.error("Exception scanning " + pom, ex);
        }
    }

    private Map<Path, Integer> performScan(Pom pom) throws Exception
    {
        Path sourceFolder = pom.projectFolder().resolve("src").resolve("main")
                .resolve("java");
        if (!Files.exists(sourceFolder) || !Files.isDirectory(sourceFolder))
        {
            return null;
        }
        Map<Path, Integer> result = new TreeMap<>();
        scanner.scan(sourceFolder, (path, score) ->
        {
            result.put(sourceFolder.relativize(path), score);
        });
        return result;
    }

    private static Path defaultSourceDir(Path projectDir)
    {
        return projectDir.resolve("src").resolve("main").resolve("java");
    }
}
