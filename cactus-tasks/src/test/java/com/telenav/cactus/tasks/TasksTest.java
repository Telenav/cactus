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
package com.telenav.cactus.tasks;

import com.mastfrog.function.state.Bool;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class TasksTest
{
    private Tasks tasks;
    private Set<String> executed;
    private List<String> rolledBack;
    private Set<String> logged;

    @Test
    public void testReentrantTasksAreRun() throws Exception
    {
        Tasks tasks = new Tasks("reentrant", this.executed::add);
        Bool bRun = Bool.create();
        Bool cRun = Bool.create();
        Bool dRun = Bool.create();
        Bool eRun = Bool.create();

        tasks.add("a", () ->
        {
            tasks.add("b", (ThrowingRunnable) bRun::set);
            tasks.add("c", () ->
            {
                cRun.set();
                tasks.add("d", (ThrowingRunnable) dRun::set);
                tasks.add("e", (ThrowingRunnable) eRun::set);
            });
        });
        tasks.execute();
        assertRan("a", "b", "c", "d", "e", "reentrant");

        for (Bool be : new Bool[]
        {
            bRun, cRun, dRun, eRun
        })
        {
            assertTrue(be.getAsBoolean());
        }
    }

    @Test
    public void testReentrancyInTaskGroups() throws Exception
    {
        Tasks tasks = new Tasks("reentrantGroup", this.executed::add);
        Bool bRun = Bool.create();
        Bool cRun = Bool.create();
        Bool dRun = Bool.create();
        Bool eRun = Bool.create();
        Bool gRun = Bool.create();
        Bool hRun = Bool.create();
        Bool iRun = Bool.create();

        TaskGroup grp = tasks.group("a");

        grp.add("a", () ->
        {
            tasks.add("b", (ThrowingRunnable) bRun::set);
            grp.add("c", () ->
            {
                cRun.set();
                tasks.add("d", (ThrowingRunnable) dRun::set);
                grp.add("e", (ThrowingRunnable) eRun::set);
                TaskGroup sub = grp.group("f");
                sub.add("g", (ThrowingRunnable) gRun::set);
                sub.add("subSub", () ->
                {
                    hRun.set();
                    tasks.add("i", (ThrowingRunnable) iRun::set);
                });
            });
        });
        tasks.execute();
        assertRan("a", "reentrantGroup", "b", "c", "d", "e", "f", "g", "i",
                "subSub");
        for (Bool be : new Bool[]
        {
            bRun, cRun, dRun, eRun, gRun, hRun, iRun
        })
        {
            assertTrue(be.getAsBoolean());
        }
    }

    @Test
    public void testEmptyGroupsIsEmptyTasks()
    {
        assertTrue(tasks.isEmpty());
        tasks.group("a");
        tasks.group("b", grp ->
        {
            grp.group("c");
            grp.group("d", d ->
            {
                d.group("e");
            });
        });
        assertTrue(tasks.isEmpty());
        assertEquals("", tasks.toString());
    }

    @Test
    public void testSimpleRun() throws Exception
    {
        assertTrue(tasks.isEmpty());
        addOne("a").addOne("b").addOne("c").addOne("d");
        assertEquals(" * a\n"
                + " * b\n"
                + " * c\n"
                + " * d", tasks.toString());
        tasks.execute();
        assertRan("a", "b", "c", "d");
        assertTrue(tasks.isEmpty());
    }

    @Test
    public void testNestedRun() throws Exception
    {
        assertTrue(tasks.isEmpty());
        tasks.group("a", a ->
        {
            a.add("a1", oneRun("a1"));
            a.add("a2", oneRun("a3"));
            a.group("a3", a3 ->
            {
                a3.add("a3a", oneRun("a3a"));
                a3.add("a3b", oneRun("a3b"));
                a3.add("a3c", oneRun("a3c"));
                a3.group("a3d", a3d ->
                {
                    a3d.add("a3d1", oneRun("a3d1"));
                    a3d.add("a3d2", oneRun("a3d2"));
                });
            });
        }).add("b", oneRun("b"))
                .group("c", c ->
                {
                    c.add("c1", oneRun("c1"));
                    c.group("c2", c2 ->
                    {
                        c2.add("c2a", oneRun("c2a"));
                    });
                })
                .group("shouldBeAbsent1", grp ->
                {
                    grp.group("shouldBeAbsent2");
                });

        assertFalse(tasks.toString().contains("shouldBeAbsent1"),
                () -> "Empty groups should not appear in string representation, but got " + tasks);
        assertFalse(tasks.toString().contains("shouldBeAbsent2"),
                () -> "Empty groups should not appear in string representation, but got " + tasks);

        assertTrue(tasks.toString().contains("\n   * a1"),
                () -> "Indentation is wrong? " + tasks);
        assertTrue(tasks.toString().contains("\n       * a3d1"),
                () -> "Indentation is wrong? " + tasks);

        tasks.execute();

        assertRan("a1", "a3c", "a3b", "a3", "b", "a3d1", "c2a", "a3d2", "a3a",
                "c1");
    }

    @Test
    public void testSimpleRollback() throws Exception
    {
        addOneRollback("a");
        addOneRollback("b");
        addOneRollback("c");
        addOneRollback("d");
        tasks.add("e", oneThrow("e", () -> new FailureOne()));
        tasks.add("f", oneThrow("e", () -> new FailureTwo()));

        FailureOne failure = expectThrown(FailureOne.class);
        assertNotNull(failure);
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertRan("a", "b", "c", "d", "e");
        assertDidNotRun("f");
        assertRolledBack("a", "b", "c", "d");
        assertNotRolledBack("e", "f");
        assertRollBackOrder("d", "c", "b", "a");
    }

    @Test
    public void testGroupRollback() throws Exception
    {
        addOneRollback("a");
        addOneRollback("b");
        tasks.group("group", grp ->
        {
            grp.add("c", oneRollback("c"));
            grp.group("nestedGroup", ngrp ->
            {
                grp.add("d", oneRollback("d"));
            });
        });
        tasks.add("e", oneThrow("e", () -> new FailureOne()));
        tasks.add("f", oneThrow("e", () -> new FailureTwo()));

        FailureOne failure = expectThrown(FailureOne.class);
        assertNotNull(failure);
        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertRan("a", "b", "c", "d", "e");
        assertDidNotRun("f");
        assertRolledBack("a", "b", "c", "d");
        assertNotRolledBack("e", "f");
        assertRollBackOrder("d", "c", "b", "a");
    }

    @Test
    public void testRollbackRunsEvenWhenOtherRollbacksThrow() throws Exception
    {
        addOneRollback("a");
        addOneRollback("b");
        addOneRollbackThrow("c", () -> new FailureOne());
        addOneRollback("d");
        addOneRollback("e");
        addOneRollbackThrow("f", () -> new FailureTwo());
        addOneRollback("g");
        TaskGroup h = tasks.group("h");
        h.add("i", oneRollback("i"));
        h.add("j", oneRollbackThrow("j", () -> new ErrorOne()));
        addOne("k");
        tasks.add("l", () ->
        {
            throw new ErrorTwo();
        });

        ErrorTwo thrown = expectThrown(ErrorTwo.class);
        List<Class<?>> exceptionChain = unwind(thrown);

        assertEquals(asList(ErrorTwo.class, ErrorOne.class, FailureTwo.class,
                FailureOne.class),
                exceptionChain,
                "All thrown exceptions and errors should have been captured");

        assertRan("a", "b", "c", "d", "e", "f", "g", "i", "j", "k");
        assertRollBackOrder("i", "g", "e", "d", "b", "a");
    }

    @Test
    public void testOtherThrowableBypasses() throws Exception
    {
        addOneRollback("a");
        addOneRollback("b");
        addOneRollback("c");
        addOneRollback("d");
        tasks.add("x", () ->
        {
            chuck(new OtherThrowable());
        });
        expectThrown(OtherThrowable.class);
        assertRan("a", "b", "c", "d");
        assertTrue(rolledBack.isEmpty(),
                "Rollback should not have run for random throwable");
    }

    private <E extends Throwable> E expectThrown(Class<E> type)
    {
        try
        {
            tasks.execute();
            throw new AssertionError("Nothing was thrown - expected "
                    + type.getSimpleName());
        }
        catch (Throwable t)
        {
            if (type.isInstance(t))
            {
                return type.cast(t);
            }
            return chuck(t);
        }
    }

    TasksTest addOne(String name)
    {
        tasks.add(name, oneRun(name));
        return this;
    }

    TasksTest addOneRollback(String name)
    {
        tasks.add(name, oneRollback(name));
        return this;
    }

    TasksTest addOneRollbackThrow(String name, Supplier<Throwable> thrown)
    {
        tasks.add(name, oneRollbackThrow(name, thrown));
        return this;
    }

    private ThrowingRunnable oneRun(String s)
    {
        return () -> executed.add(s);
    }

    private ThrowingRunnable oneThrow(String name, Supplier<Throwable> thrower)
    {
        return () ->
        {
            executed.add(name);
            chuck(thrower.get());
        };
    }

    private ThrowingSupplier<ThrowingRunnable> oneRollbackThrow(String name,
            Supplier<Throwable> thrower)
    {
        return () ->
        {
            executed.add(name);
            return () -> chuck(thrower.get());
        };
    }

    private ThrowingSupplier<ThrowingRunnable> oneRollback(String name)
    {
        return () ->
        {
            executed.add(name);
            return ()
                    -> rolledBack.add(name);
        };
    }

    private void assertRan(String... prts)
    {
        assertEquals(new HashSet<>(asList(prts)), executed);
    }

    private void assertRolledBack(String... prts)
    {
        assertEquals(new HashSet<>(asList(prts)), new HashSet<>(rolledBack));
    }

    private void assertNotRolledBack(String... prts)
    {
        Set<String> rb = new HashSet<>(rolledBack);
        Set<String> expected = new HashSet<>(asList(prts));
        rb.retainAll(expected);
        assertTrue(rb.isEmpty(), "Unexpected rollbacks: " + rb);
    }

    private void assertRollBackOrder(String... prts)
    {
        assertEquals(rolledBack, asList(prts));
    }

    private void assertDidNotRun(String... parts)
    {
        Set<String> set = new HashSet<>(asList(parts));
        assertEquals(set.size(), parts.length, "Passed duplicates");
        set.retainAll(executed);
        assertEquals(0, set.size(),
                "Some unexpected tasks were run: " + set);
    }

    @BeforeEach
    public void setUp()
    {
        logged = new HashSet<>();
        tasks = new Tasks(logged::add);
        executed = new HashSet<>();
        rolledBack = new ArrayList<>();
    }

    static List<Class<?>> unwind(Throwable thrown)
    {
        List<Class<?>> result = new ArrayList<>();
        unwind(thrown, result);
        return result;
    }

    static void unwind(Throwable thrown, List<Class<?>> into)
    {
        if (thrown == null)
        {
            return;
        }
        into.add(thrown.getClass());
        Throwable[] supp = thrown.getSuppressed();
        if (supp != null)
        {
            for (Throwable t : supp)
            {
                unwind(t, into);
            }
        }
        unwind(thrown.getCause(), into);
    }

    static class FailureOne extends Exception
    {

    }

    static class FailureTwo extends Exception
    {

    }

    static class ErrorOne extends Error
    {

    }

    static class ErrorTwo extends Error
    {

    }

    static class OtherThrowable extends Throwable
    {

    }

}
