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

import java.util.function.Consumer;

/**
 * A set of tasks which can be added to and run exactly once.
 *
 * @author Tim Boudreau
 */
public interface TaskSet extends TaskGroup
{

    /**
     * Run any added tasks; in the case of failure, perform any added rollback
     * tasks that were produced by running the tasks. Rollbacks run regardless
     * of the failure of other rollbacks.
     * <p>
     * If an exception is thrown by any task, execution of the remaining tasks
     * is aborted, and any rollback code produced by tasks is run in reverse
     * task order.
     * </p><p>
     * If a rollback job throws an exception, that exception is attached to the
     * exception that triggered the rollback.
     * </p>
     *
     * @throws Exception if something goes wrong
     */
    void execute() throws Exception;

    /**
     * Create a new task set with a generic name for the root task and default
     * logger.
     *
     * @return A task set
     */
    public static TaskSet newTaskSet()
    {
        return new Tasks();
    }

    /**
     * Create a new task set with a generic name for the root task.
     *
     * @param logger A consumer which will be notified when a task is run or
     * rolled back
     * @return A task set
     */
    public static TaskSet newTaskSet(Consumer<String> logger)
    {
        return new Tasks(logger);
    }

    /**
     * Create a new task set that uses the passed name for its root task name.
     *
     * @param name The loggable name of the root task
     * @return A task set
     */
    public static TaskSet newTaskSet(String name)
    {
        return new Tasks(name);
    }

    /**
     * Create a new task set that uses the passed name for its root task name.
     *
     * @param name The loggable name of the root task
     * @param logger A consumer which will be notified when a task is run or
     * rolled back
     * @return A task set
     */
    public static TaskSet newTaskSet(String name, Consumer<String> logger)
    {
        return new Tasks(name, logger);
    }
}
