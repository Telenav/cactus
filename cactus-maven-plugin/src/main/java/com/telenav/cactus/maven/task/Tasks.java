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
package com.telenav.cactus.maven.task;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.telenav.cactus.maven.log.BuildLog;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.util.Collections.unmodifiableList;

/**
 * A structured set of tasks to run, with support for logging work as it
 * happens, nested groups of related tasks that must run before any others,
 * formatted string representation, and the ability for a task to provide some
 * work to perform to roll back what it did if an error is encountered later.
 *
 * @author Tim Boudreau
 */
final class Tasks implements TaskSet
{
    private final List<Task> children = new CopyOnWriteArrayList<>();
    private final Consumer<String> log;
    private final String name;

    Tasks()
    {
        this("root");
    }

    Tasks(String name)
    {
        this.name = name;
        log = BuildLog.get().child("tasks");
    }

    Tasks(String name, Consumer<String> log)
    {
        this.name = name;
        this.log = log;
    }

    Tasks(Consumer<String> log)
    {
        this("root", log);
    }

    @Override
    public Iterator<Task> iterator()
    {
        return unmodifiableList(children).iterator();
    }

    @Override
    public Tasks add(String name, ThrowingRunnable run)
    {
        children.add(new TaskImpl(notNull("name", name), notNull("run", run)));
        return this;
    }

    @Override
    public Tasks add(String name, ThrowingSupplier<ThrowingRunnable> run)
    {
        children.add(new RollbackTaskImpl(notNull("name", name), notNull("run",
                run)));
        return this;
    }

    @Override
    public TaskGroup group(String name)
    {
        TaskGroup group = new TaskGroupImpl(name);
        children.add(group);
        return group;
    }

    @Override
    public Tasks group(String name, ThrowingConsumer<TaskGroup> groupConsumer)
    {
        TaskGroup result = group(name);
        groupConsumer.toNonThrowing().accept(result);
        return this;
    }

    @Override
    public void execute() throws Exception
    {
        try
        {
            Rollback rollback = new Rollback();
            rollback.executeWithRollback(() ->
            {
                for (Task kid : children)
                {
                    log.accept(kid.name());
                    kid.accept(log, rollback);
                }
            });
        }
        finally
        {
            children.clear();
        }
    }

    @Override
    public Tasks add(Task task)
    {
        children.add(notNull("task", task));
        return this;
    }

    @Override
    public void accept(Consumer<String> logger, Rollback rollbacks) throws Exception
    {
        for (Task t : children)
        {
            t.accept(logger, rollbacks);
        }
    }

    @Override
    public StringBuilder stringify(int depth, StringBuilder into)
    {
        return TaskSet.super.stringify(depth, into);
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (Task t : children)
        {
            t.stringify(sb);
        }
        return sb.toString();
    }

    public boolean isEmpty()
    {
        boolean result = children.isEmpty();
        if (!result)
        {
            result = true;
            for (Task t : children)
            {
                if (!t.isEmpty())
                {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }
}
