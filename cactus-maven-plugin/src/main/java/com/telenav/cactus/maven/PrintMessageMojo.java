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
package com.telenav.cactus.maven;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.concurrent.ConcurrentLinkedList.fifo;
import static com.telenav.cactus.maven.trigger.RunPolicies.LAST_CONTAINING_GOAL;
import static java.lang.Runtime.getRuntime;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Simply prints a highly visible message to the console. Uses a JVM shutdown
 * hook to print the message after all maven output. The message can be set to
 * be printed always, only on build success or only on build failure.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "print-message", threadSafe = true)
@BaseMojoGoal("print-message")
public class PrintMessageMojo extends BaseMojo
{

    /**
     * The message to print.
     */
    @Parameter(property = "cactus.message", required = true)
    private String message;

    /**
     * Nullable boolean: If set to true, print the message only if the execution
     * result HAS exceptions. If set to false, print the message only if the
     * execution result DOES NOT have exceptions. If unset, the message is
     * always printed.
     */
    @Parameter(property = "cactus.message.on.failure")
    private Boolean onFailure;

    private static final AtomicBoolean HOOK_ADDED = new AtomicBoolean();
    private static final ConcurrentLinkedList<PrintableMessage> messages = fifo();

    public PrintMessageMojo()
    {
        super(LAST_CONTAINING_GOAL);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (message != null)
        {
            addMessage(message);
        }
    }
    
    /**
     * Allows other mojos to add meessages.
     * 
     * @param msg A message
     * @param session The session
     * @param onFailure Whether on success or failure.
     */
    static void publishMessage(CharSequence msg, MavenSession session, boolean onFailure) {
        messages.push(new PrintableMessage(msg, session, onFailure));
        if (HOOK_ADDED.compareAndSet(false, true))
        {
            Thread t = new Thread(() -> emitMessages(), "shutdown-messages");
            getRuntime().addShutdownHook(t);
        }
    }

    void addMessage(CharSequence msg)
    {
        messages.push(new PrintableMessage(msg, session(), onFailure));
        if (HOOK_ADDED.compareAndSet(false, true))
        {
            Thread t = new Thread(() -> emitMessages(), getClass()
                    .getName());
            getRuntime().addShutdownHook(t);
        }
    }

    private static void emitMessages()
    {
        // We can be invoked multiple times with the same message in an
        // aggregate build.
        Set<PrintableMessage> seen = new HashSet<>();
        List<PrintableMessage> msgs = new LinkedList<>();
        messages.drain(msgs::add);
        boolean first = true;
        if (!msgs.isEmpty())
        {
            for (PrintableMessage s : msgs)
            {
                if (!s.shouldPrint())
                {
                    continue;
                }
                if (seen.add(s))
                {
                    if (first)
                    {
                        System.out.println(
                                "\n*********************************************************\n\n");
                        first = false;
                    }
                    emitMessage(s.toString());
                }
            }
            System.out.println(
                    "\n\n*********************************************************\n");
        }
    }

    private static void emitMessage(String msg)
    {
        // Code formatters for POM files will want to heavily indent
        // message lines.  Rather than fight this, we will just trim
        // the lines.
        for (String s : msg.split("\n"))
        {
            s = s.trim().replaceAll("\\\\n", "\n")
                    .replaceAll("\\\\t", "\t");
            System.out.println(s);
        }
    }
}
