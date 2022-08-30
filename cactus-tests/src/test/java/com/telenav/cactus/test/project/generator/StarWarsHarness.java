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

import com.mastfrog.function.BooleanConsumer;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingBiFunction;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingQuadConsumer;
import com.mastfrog.function.throwing.ThrowingQuadFunction;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.function.throwing.ThrowingTriFunction;
import com.telenav.cactus.git.Branches;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.test.project.ProjectWrapper;
import com.telenav.cactus.test.project.starwars.StarWars;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.TestInfo;

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static com.telenav.cactus.maven.mojobase.AutomergeTag.AUTOMERGE_TAG_PREFIX;
import static com.telenav.cactus.test.project.starwars.StarWars.starWars;
import static java.lang.System.getenv;
import static java.lang.System.setProperty;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 *
 * @author Tim Boudreau
 */
public final class StarWarsHarness
{
    public static final boolean TESTS_DISABLED;
    private static final boolean DEFAULT_DEBUG;
    private static final boolean SLF4J_DEBUG;

    static
    {
        DEFAULT_DEBUG = Boolean.getBoolean(
            "cactus.test.debug") || 
                "true".equals(getenv("CACTUS_TEST_DEFAULT_DEBUG"));
        SLF4J_DEBUG  = Boolean.getBoolean(
            "cactus.test.slf4j.debug") 
                || "true".equals(getenv("CACTUS_SLF4J_DEBUG"));
        TESTS_DISABLED = Boolean.getBoolean("cactus.tests.skip")
                || "true".equals(getenv("CACTUS_TESTS_SKIP"));
    }

    public static final String WOOKIES_FAMILY = "wookies";
    private final AtomicBoolean failed = new AtomicBoolean();
    private final TestInfo info;
    private final StarWars starwars;

    static
    {
        if (SLF4J_DEBUG)
        {
            debug();
        }
    }

    public StarWarsHarness(TestInfo info) throws IOException
    {
        this.info = info;
        starwars = starWars();
        starwars.superpomsProject().build();
        starwars.build();
    }

    private StarWarsHarness(StarWarsHarness harn) throws IOException
    {
        this.info = harn.info;
        this.starwars = harn.starwars().newClone();
        starwars.superpomsProject().build();
        starwars.build();
    }

    public StarWarsHarness cloneHarness() throws IOException
    {
        return new StarWarsHarness(this);
    }

    public TestInfo info()
    {
        return info;
    }

    public StarWars starwars()
    {
        return starwars;
    }

    public static void debug()
    {
        setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
    }

    public boolean failed()
    {
        return failed.get();
    }

    public void teardown() throws IOException
    {
        if (failed.get())
        {
            System.out.println("Test " + info.getDisplayName() + " failed.");
            System.out.println(
                    "Generated repositories left behind in " + starwars
                            .parentRoot());
        }
        else
        {
            starwars.cleanup();
        }
    }

    public void runTest(ThrowingRunnable run) throws Exception
    {
        runTest(DEFAULT_DEBUG, run);
    }

    public void runTest(boolean debug, ThrowingRunnable run) throws Exception
    {
        BuildLog log = new BuildLog("", info.getDisplayName());
        log.run(() ->
        {
            runTest(debug, run, failed::set);
        });
    }

    public static void runTest(boolean debug, ThrowingRunnable run,
            BooleanConsumer onFailure)
    {
        if (TESTS_DISABLED)
        {
            return;
        }
        try
        {
            if (debug)
            {
                MavenCommand.debug(run);
            }
            else
            {
                run.toNonThrowing().run();
            }
            onFailure.accept(false);
        }
        catch (Throwable thrown)
        {
            onFailure.accept(true);
            chuck(thrown);
        }
    }

    public static void assertDirty(ProjectWrapper... wrapper)
    {
        for (ProjectWrapper w : wrapper)
        {
            assertTrue(w.getCheckout().isDirty(), "Not dirty: " + w
                    .getCheckout().loggingName());
        }
    }

    public static void pushNewBranch(ProjectWrapper... wrappers)
    {
        for (ProjectWrapper w : wrappers)
        {
            assertTrue(w.pushCreatingBranch(), () -> w.getCheckout()
                    .loggingName()
                    + " pushing branch " + w.getCheckout().branch().get());
        }
    }

    public static final Map<ProjectWrapper, String> findAutomergeTags(
            ProjectWrapper... wrappers)
    {
        Map<ProjectWrapper, String> result = new HashMap<>();
        for (ProjectWrapper w : wrappers)
        {
            for (String t : w.getCheckout().tags())
            {
                if (t.startsWith(AUTOMERGE_TAG_PREFIX))
                {
                    result.put(w, t);
                }
            }
        }
        return result;
    }

    public static Map<ProjectWrapper, String> heads(ProjectWrapper... wrapper)
    {
        Map<ProjectWrapper, String> result = new HashMap<>();
        for (ProjectWrapper w : wrapper)
        {
            String head = w.getCheckout().head();
            result.put(w, head);
        }
        return result;
    }

    public static void modifySources(ProjectWrapper... wrappers) throws IOException
    {
        for (ProjectWrapper w : wrappers)
        {
            w.modifyPomFile("Main");
        }
    }

    public static void createBranches(String name, ProjectWrapper... wrappers)
    {
        for (ProjectWrapper w : wrappers)
        {
            assertTrue(w.newBranch(name),
                    w.getCheckout().loggingName() + " " + name);
        }
        pushNewBranch(wrappers);
    }

    public static Map<String, Object> args(Object... parts)
    {
        Map<String, Object> m = new LinkedHashMap<>();
        assertTrue(parts.length % 2 == 0);
        for (int i = 0; i < parts.length; i += 2)
        {
            m.put(parts[i].toString(), parts[i + 1]);
        }
        return m;
    }

    public static void assertBranchExists(String name, boolean local,
            ProjectWrapper... w)
    {
        assertBranch(true, name, local, w);
    }

    public static void assertBranchDoesNotExist(String name, boolean local,
            ProjectWrapper... w)
    {
        assertBranch(false, name, local, w);
    }

    public static void assertHasAutomergeTag(ProjectWrapper... pw)
    {
        StringBuilder sb = new StringBuilder();
        for (ProjectWrapper w : pw)
        {
            List<String> tags = w.getCheckout().tags();
            boolean found = false;
            for (String t : tags)
            {
                if (t.startsWith("automerge-"))
                {
                    found = true;
                }
            }
            if (!found)
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(w.getCheckout().loggingName())
                        .append(" has no automerge tag in ").append(tags);
            }
        }
        assertEquals(0, sb.length(), sb.toString());
    }

    public static void assertTag(String tag,
            ProjectWrapper... inProjectCheckouts)
    {
        for (ProjectWrapper w : inProjectCheckouts)
        {
            List<String> tags = w.getCheckout().tags();
            assertTrue(tags.contains(tag),
                    "Tag '" + tag + "' not found in " + tags
                    + " for " + w.getCheckout().loggingName());
        }
    }

    public static void assertHead(String head, ProjectWrapper w,
            String... ofBranches)
    {
        if (ofBranches.length == 0)
        {
            assertEquals(head, w.getCheckout().head(), "Wrong head for " + w
                    .getCheckout().loggingName());
        }
        else
        {
            for (String s : ofBranches)
            {
                assertEquals(w.getCheckout().headOf(s), head,
                        "Wrong head of branch " + s + " for " + w.getCheckout()
                                .loggingName());
            }
        }
    }

    public static void assertBranch(boolean exists, String name, boolean local,
            ProjectWrapper... w)
    {
        StringBuilder sb = new StringBuilder();
        for (ProjectWrapper p : w)
        {
            GitCheckout checkout = p.getCheckout();
            Branches branches = checkout.branches();
            Optional<Branches.Branch> opt = branches.find(name, local);
            if (opt.isPresent() != exists)
            {
                if (sb.length() > 0)
                {
                    sb.append("\n");
                }
                if (exists)
                {
                    sb.append("Branch '").append(name)
                            .append("' does not exist in ").append(checkout
                            .loggingName());
                }
                else
                {
                    sb.append("Branch '").append(name)
                            .append("' exists in ")
                            .append(checkout.loggingName())
                            .append(" should not, in ")
                            .append(checkout.checkoutRoot());
                }
            }
        }
        if (sb.length() > 0)
        {
            fail(sb.toString());
        }
    }

    public void run(ThrowingConsumer<StarWars> consumer) throws Exception
    {
        consumer.accept(starwars);
    }

    public <T> T run(ThrowingFunction<StarWars, T> consumer) throws Exception
    {
        return consumer.apply(starwars);
    }

    public <T, A> T run(A arg, ThrowingBiFunction<A, StarWars, T> consumer)
            throws Exception
    {
        return consumer.apply(arg, starwars);
    }

    public <A> void run(A arg, ThrowingBiConsumer<A, StarWars> consumer) throws Exception
    {
        consumer.accept(arg, starwars);
    }

    public <T, A, B> T run(A arg, B b,
            ThrowingTriFunction<A, B, StarWars, T> consumer)
            throws Exception
    {
        return consumer.apply(arg, b, starwars);
    }

    public <A, B> void run(A arg, B b,
            ThrowingTriConsumer<A, B, StarWars> consumer) throws Exception
    {
        consumer.accept(arg, b, starwars);
    }

    public <T, A, B, C> T run(A arg, B b, C c,
            ThrowingQuadFunction<A, B, C, StarWars, T> consumer)
            throws Exception
    {
        return consumer.apply(arg, b, c, starwars);
    }

    public <A, B, C> void run(A arg, B b, C c,
            ThrowingQuadConsumer<A, B, C, StarWars> consumer) throws Exception
    {
        consumer.accept(arg, b, c, starwars);
    }

    public StarWars anotherClone() throws IOException
    {
        return starwars.newClone();
    }

}
