package com.telenav.cactus.maven;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Simply prints a highly visible message to the console. Uses a JVM shutdown
 * hook to print the message after all maven output.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.KEEP_ALIVE,
        name = "print-message", threadSafe = true)
public class PrintMessageMojo extends BaseMojo
{

    /**
     * The message to print.
     */
    @Parameter(property = "cactus.message", required = true)
    private String message;

    private static final AtomicBoolean HOOK_ADDED = new AtomicBoolean();
    private static final ConcurrentLinkedList<String> messages = ConcurrentLinkedList
            .fifo();

    public PrintMessageMojo()
    {
        super(RunPolicies.EVERY);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (message != null)
        {
            addMessage(message);
        }
    }

    private void addMessage(String msg)
    {
        messages.push(msg);
        if (HOOK_ADDED.compareAndSet(false, true))
        {
            Thread t = new Thread(PrintMessageMojo::emitMessages, getClass()
                    .getName());
            Runtime.getRuntime().addShutdownHook(t);
        }
    }

    private static void emitMessages()
    {
        // We can be invoked multiple times with the same message in an
        // aggregate build.
        Set<String> seen = new HashSet<>();
        List<String> msgs = new LinkedList<>();
        messages.drain(msgs::add);
        boolean first = true;
        if (!msgs.isEmpty())
        {
            for (String s : msgs)
            {
                if (seen.add(s))
                {
                    if (first)
                    {
                        System.out.println(
                                "\n*********************************************************\n\n");
                        first = false;
                    }
                    emitMessage(s);
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
