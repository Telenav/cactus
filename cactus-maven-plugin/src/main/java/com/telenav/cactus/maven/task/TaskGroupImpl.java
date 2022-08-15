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

import com.mastfrog.util.preconditions.Checks;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 *
 * @author Tim Boudreau
 */
final class TaskGroupImpl implements TaskGroup
{
    private final List<Task> children = new CopyOnWriteArrayList<>();
    final String name;

    TaskGroupImpl(String name)
    {
        this.name = Checks.notNull("name", name);
    }

    @Override
    public Iterator<Task> iterator()
    {
        return Collections.unmodifiableList(children).iterator();
    }

    @Override
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

    @Override
    public TaskGroup add(Task task)
    {
        children.add(task);
        return this;
    }

    @Override
    public String name()
    {
        return name;
    }

    @Override
    public void accept(Consumer<String> log, Rollback rollbacks) throws Exception
    {
        log.accept(name());
        for (Task child : children)
        {
            child.accept(log, rollbacks);
        }
    }

    @Override
    public String toString()
    {
        return stringify();
    }

}
