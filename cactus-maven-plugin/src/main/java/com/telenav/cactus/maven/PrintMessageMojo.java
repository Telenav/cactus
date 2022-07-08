package com.telenav.cactus.maven;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Simply prints a highly visible message to the console. Uses a JVM shutdown
 * hook to print the message after all maven output. The message can be set to
 * be printed always, only on build success or only on build failure.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
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
    private static final ConcurrentLinkedList<PrintableMessage> messages = ConcurrentLinkedList
            .fifo();

    public PrintMessageMojo()
    {
        super(RunPolicies.LAST_CONTAINING_GOAL);
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
    static void publishMessage(String msg, MavenSession session, boolean onFailure) {
        messages.push(new PrintableMessage(msg, session, onFailure));
        if (HOOK_ADDED.compareAndSet(false, true))
        {
            Thread t = new Thread(() -> emitMessages(), "shutdown-messages");
            Runtime.getRuntime().addShutdownHook(t);
        }
    }

    void addMessage(String msg)
    {
        messages.push(new PrintableMessage(msg, session(), onFailure));
        if (HOOK_ADDED.compareAndSet(false, true))
        {
            Thread t = new Thread(() -> emitMessages(), getClass()
                    .getName());
            Runtime.getRuntime().addShutdownHook(t);
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
