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

import com.mastfrog.function.throwing.ThrowingBiConsumer;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * A named task which can be executed and logged; the work it performs is
 * performed by the <code>accept</code> method of the inherited
 * <code>ThrowingBiConsumer</code> interface, which takes a string consumer for
 * emitting logging information, and a Rollback to which rollback tasks can be
 * attached.
 * <p>
 * The framework's implementation of Task should usually be used - the one case
 * for implementing Task directly is where you need to directly access the
 * Rollback instance.
 * </p>
 *
 * @author Tim Boudreau
 */
public interface Task extends ThrowingBiConsumer<Consumer<String>, Rollback>
{
    /**
     * The name of this task, describing what it does or the category its child
     * tasks are in.
     *
     * @return A name
     */
    String name();

    /**
     * Determine if this task has no work to do.
     *
     * @return True if this task has no work to do
     */
    default boolean isEmpty()
    {
        return false;
    }

    /**
     * Convert this task into a markdown-style bullet list with appropriate
     * indenting for nested tasks.
     *
     * @return A string
     */
    default String stringify()
    {
        return stringify(new StringBuilder()).toString();
    }

    /**
     * Include this task in a markdown-style bullet list with appropriate
     * indenting for nested tasks, in the passed StringBuilder.
     *
     * @param into The StringBuilder to append to
     * @return A string
     */
    default StringBuilder stringify(StringBuilder into)
    {
        return stringify(0, into);
    }

    /**
     * Include this task in a markdown-style bullet list with appropriate
     * indenting for nested tasks, in the passed StringBuilder.
     *
     * @param depth The nesting depth at to indent this task to and the basis
     * for indentingany child tasks
     * @param into The StringBuilder to append to
     * @return A string
     */
    default StringBuilder stringify(int depth, StringBuilder into)
    {
        // Format the task (and any children) as a markdown-like
        // bulleted list
        char[] c = new char[depth * 2];
        Arrays.fill(c, ' ');
        if (into.length() > 0 && into.charAt(into.length() - 1) != '\n')
        {
            into.append('\n');
        }
        into.append(c).append(" * ").append(name());
        return into;
    }

}
