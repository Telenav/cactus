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

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;

/**
 * A group of tasks which should be run in the order tasks were added to it.
 *
 * @author Tim Boudreau
 */
public interface TaskGroup extends Task, Iterable<Task>
{
    /**
     * Add a task implementation.
     *
     * @param task A task
     * @return this
     */
    TaskGroup add(Task task);

    @Override
    default StringBuilder stringify(int depth, StringBuilder into)
    {
        if (isEmpty())
        {
            return into;
        }
        Task.super.stringify(depth, into);
        for (Task child : this)
        {
            child.stringify(depth + 1, into);
        }
        return into;
    }

    /**
     * Add a runnable to this set of tasks
     *
     * @param name A textual description of what the passed runnable will do,
     * suitable for logging
     * @param code The thing to run
     * @return this
     */
    default TaskGroup add(String name, ThrowingRunnable code)
    {
        return add(new TaskImpl(name, code));
    }

    /**
     * Add some code to run to this set of tasks, which may return a
     * <code>ThrowingRunnable</code> which should be used to roll back whatever
     * the code did, in the event that a subsequent task throws an exception or
     * error.
     * <p>
     * In the event of rollback, the framework guarantees that the rollback code
     * <b>will</b> run <i>regardless of whether previously run rollback code
     * threw an exception</i>, unless the throwable thrown by a rollback or a
     * task is neither an instance of <code>java.lang.Exception</code> nor
     * <code>java.lang.Error</code>.
     * </p>
     *
     * @param name A textual description of what the passed code will do,
     * suitable for logging
     * @param code The thing to run, which (optionally) returns a
     * <code>ThrowingRunnable</code> that can undo
     * @return this
     */
    default TaskGroup add(String name, ThrowingSupplier<ThrowingRunnable> code)
    {
        return add(new RollbackTaskImpl(name, code));
    }

    /**
     * Creates a new child task group attached to this group. Note: When logging
     * or using <code>toString()</code> on a <code>TaskGroup</code> a group only
     * appears in the string representation if it contains non-empty children.
     *
     * @param name The name for the group
     * @return A new task group
     */
    default TaskGroup group(String name)
    {
        TaskGroup result = new TaskGroupImpl(name);
        add(result);
        return result;
    }

    /**
     * Creates a new child task group attached to this group and passes it to
     * the passed consumer. Note: When logging or using <code>toString()</code>
     * on a <code>TaskGroup</code> a group only appears in the string
     * representation if it contains non-empty children.
     *
     * @param name The name for the group
     * @param childConsumer A consumer which will add to the tasks
     * @return this - the created group is accessible only via the consumer
     */
    default TaskGroup group(String name,
            ThrowingConsumer<TaskGroup> childConsumer)
    {
        TaskGroup child = group(name);
        childConsumer.toNonThrowing().accept(child);
        return this;
    }
}
