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
package com.telenav.cactus.test.project.generator;

import com.telenav.cactus.cli.CliCommand;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.GitCommand;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mastfrog.util.file.FileUtils.deltree;
import static com.telenav.cactus.cli.ProcessResultConverter.exitCodeIsZero;
import static com.telenav.cactus.git.GitCheckout.checkout;
import static com.telenav.cactus.git.NeedPushResult.NO;
import static com.telenav.cactus.git.NeedPushResult.YES;
import static com.telenav.cactus.test.project.generator.RepositoriesGenerator.initOriginRepo;
import static com.telenav.cactus.util.PathUtils.temp;
import static java.lang.System.currentTimeMillis;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public class NeedsPullTest
{
    Path root;
    Path base;
    GitCheckout clone1;
    GitCheckout clone2;

    @Test
    public void testIt() throws Exception
    {
        assertTrue(clone1.remoteHead().isPresent(),
                "Clone 1 has no remote head");

        assertTrue(clone2.remoteHead().isPresent(),
                "Clone 2 has no remote head");

        assertEquals(clone1.head(), clone1.remoteHead().get(),
                "Initial state of clone is inconsistent");

        assertEquals(clone2.head(), clone2.remoteHead().get(),
                "Initial state of clone is inconsistent");

        assertFalse(clone1.isDirty(),
                "Initial state of repo should be non-dirty");

        assertFalse(clone1.hasUntrackedFiles(),
                "Initial state of repo should not show untracked files");

        writeFile("stuff.txt", "This is some stuff.\nIt has lots of stuff.\n");
        sync();
        assertTrue(clone1.hasUntrackedFiles(),
                "After creating a file, it should be detected as untracked");

        assertFalse(clone1.isDirty(),
                "With an added untracked file but no changes to tracked files, "
                + "state should not be dirty");

        writeFile("README.md",
                "This readme has been rewritten\n. How about that?\n");

        assertTrue(clone1.isDirty(),
                "After rewrite of readme, repo should be dirty");

        assertTrue(clone1.hasUntrackedFiles(),
                "Untracked file should still be detected");

        assertEquals(clone1.head(), clone1.remoteHead().get(),
                "Before a commit, remote and local heads should be the same");

        assertTrue(clone1.addAll(), "Add all failed");

        assertTrue(clone1.commit("This is a commit"), "Commit failed");

        assertNotEquals(clone1.head(), clone1.remoteHead().get(),
                "Remote head should no longer be same as local after a commit");

        assertEquals(YES, clone1.needsPush(),
                "Repo did not detect that a push is needed after a commit");

        assertTrue(clone1.push(), "Push failed");

        assertEquals(NO, clone1.needsPush(),
                "Repo should no longer detect the need for a push after a push");

        assertFalse(clone2.isDirty(),
                "Second clone should be unaffected by changes in first");
        assertFalse(clone2.hasUntrackedFiles(),
                "Second clone should be unaffected by changes in first");

        assertFalse(clone2.needsPull(),
                "Needs pull should not be detected until a fetch");

        assertEquals(clone2.head(), clone2.remoteHead().get(),
                "Remote head should be unchanged until a fetch");

        assertTrue(clone2.fetch(), "Fetch failed");

        assertNotEquals(clone2.head(), clone2.remoteHead().get(),
                "Remote head not updated after fetch");

        assertTrue(clone2.needsPull(),
                "Needs pull not detected but heads differ.");

        assertTrue(clone2.pull(), "Pull failed");

        assertEquals(clone2.head(), clone2.remoteHead().get(),
                "Remote head should match local after a pull");

        assertTrue(exists(clone2.checkoutRoot().resolve("stuff.txt")),
                "Created file was not pulled.");
    }

    private Path writeFile(String name, String body) throws IOException
    {
        Path newFile = clone1.checkoutRoot().resolve(name);
        write(newFile, body.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING,
                CREATE);
        return newFile;
    }

    @BeforeEach
    public void setupRepos() throws Exception
    {
        root = temp().resolve(getClass().getSimpleName() + "-"
                + Long.toString(currentTimeMillis(), 36) + "-" + Integer
                .toString(ThreadLocalRandom.current().nextInt(), 36));

        base = initOriginRepo("Root", root);

        new GitCommand<>(exitCodeIsZero(), root, "clone", base.toString(),
                "clone-1").run().awaitQuietly();

        new GitCommand<>(exitCodeIsZero(), root, "clone", base.toString(),
                "clone-2").run().awaitQuietly();

        clone1 = checkout(root.resolve("clone-1")).get();
        clone2 = checkout(root.resolve("clone-2")).get();
        sync();
    }
    
    static void sync() throws InterruptedException {
        // diagnosing some github actions issues
        CliCommand.fixed("/bin/sync", Paths.get(".")).run().await();
    }

    @AfterEach
    public void deleteRepos() throws IOException
    {
        if (root != null)
        {
            deltree(root);
        }
    }
}
