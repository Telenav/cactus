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

import com.mastfrog.function.throwing.ThrowingRunnable;
import java.util.function.Consumer;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Implementation of Task.
 *
 * @author Tim Boudreau
 */
final class TaskImpl implements Task
{
    final String name;
    final ThrowingRunnable run;

    TaskImpl(String name, ThrowingRunnable run)
    {
        this.name = notNull("name", name);
        this.run = notNull("run", run);
    }

    @Override
    public void accept(Consumer<String> log, Rollback rb) throws Exception
    {
        log.accept(name);
        run.run();
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public String toString()
    {
        return stringify();
    }

}
