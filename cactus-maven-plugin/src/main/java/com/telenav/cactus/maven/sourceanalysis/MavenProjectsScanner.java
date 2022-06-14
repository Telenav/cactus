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

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;

/**
 *
 * @author Tim Boudreau
 */
public class MavenProjectsScanner
{

    private final ConcurrentLinkedList<Pom> poms;
    private final BuildLog log;
    private final SourcesScanner scanner;

    public MavenProjectsScanner(BuildLog log, SourceScorer scorer,
            Collection<? extends Pom> poms)
    {
        this.poms = ConcurrentLinkedList.lifo();
        this.scanner = new SourcesScanner(scorer);
        // Use a biconsumer for manual testing without SLF4J on the classpath
        this.log = log;
        for (Pom pom : poms)
        {
            if (!"pom".equals(pom.packaging))
            {
                this.poms.push(pom);
            }
        }
    }

    public void scan(ProjectScanConsumer c) throws InterruptedException, IOException
    {
        int count = Runtime.getRuntime().availableProcessors();
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count - 1; i++)
        {
            ForkJoinPool.commonPool().submit(() -> scanLoop(latch, c));
        }
        scanLoop(latch, c);
        latch.await();
        c.onDone();
    }

    private void scanLoop(CountDownLatch latch, ProjectScanConsumer c)
    {
        try
        {
            Pom pom;
            while ((pom = poms.pop()) != null)
            {
                scanOne(pom, c);
            }
        }
        finally
        {
            latch.countDown();
        }
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
}
