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
import com.mastfrog.function.throwing.ThrowingSupplier;
import java.util.function.Consumer;

/**
 * Implementation of Task which can produce a rollback runnable.
 *
 * @author Tim Boudreau
 */
final class RollbackTaskImpl implements Task
{
    final String name;
    final ThrowingSupplier<ThrowingRunnable> run;

    RollbackTaskImpl(String name, ThrowingSupplier<ThrowingRunnable> run)
    {
        this.name = name;
        this.run = run;
    }

    @Override
    public void accept(Consumer<String> log, Rollback rb) throws Exception
    {
        ThrowingRunnable rollbackWork = run.get();
        if (rollbackWork != null)
        {
            rb.addRollbackTask(() ->
            {
                log.accept("Rollback " + name());
                rollbackWork.run();
            });
        }
    }

    @Override
    public String toString()
    {
        return name();
    }

    @Override
    public String name()
    {
        return name;
    }

}
