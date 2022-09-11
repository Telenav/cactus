package com.telenav.cactus.test.project.generator;

import com.telenav.cactus.process.ProcessControl;
import com.telenav.cactus.process.ProcessResult;
import com.telenav.cactus.util.PathUtils;
import com.zaxxer.nuprocess.NuProcessBuilder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.sleep;
import static java.util.Collections.newSetFromMap;

/**
 *
 * @author Tim Boudreau
 */
public final class ThreadDumper implements Runnable
{

    private static final long INTERVAL_MILLIS = Duration.ofMinutes(3).toMillis();
    private static final ThreadDumper INSTANCE = new ThreadDumper();

    private final AtomicBoolean started = new AtomicBoolean();
    private final Set<ProcessControl<?, ?>> watching = newSetFromMap(
            new WeakHashMap<>());
    private final Thread thread = new Thread(this);
    private final Thread shutdownHook = new Thread(this::onShutdown);
    private final Path jstack;
    private volatile boolean shuttingDown;

    private ThreadDumper()
    {
        jstack = jstackBinary();
        thread.setName("Thread dumper");
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        thread.setDaemon(true);
    }

    private void onShutdown()
    {
        shuttingDown = true;
        if (thread.isAlive())
        {
            thread.interrupt();
        }
    }

    private static Path jstackBinary()
    {
        String jh = System.getProperty("JAVA_HOME");
        if (jh != null)
        {
            Path checkFirst = Paths.get(jh).resolve("bin");
            return PathUtils.findExecutable("jstack", checkFirst)
                    .orElseThrow(() -> new IllegalStateException(
                    "Could not find jstack binary"));
        }
        else
        {
            return PathUtils.findExecutable("jstack")
                    .orElseThrow(() -> new IllegalStateException(
                    "Could not find jstack binary"));
        }
    }

    public static void watch(ProcessControl<?, ?> c)
    {
        INSTANCE._watch(c);
    }

    private void _watch(ProcessControl<?, ?> c)
    {
        watching.add(c);
        if (started.compareAndSet(false, true))
        {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            thread.start();
        }
    }

    @Override
    public void run()
    {
        while (!shuttingDown)
        {
            try
            {
                for (ProcessControl<?, ?> pc : watching)
                {
                    if (pc.isRunning())
                    {
                        dumpOne(pc);
                    }
                }
                sleep(INTERVAL_MILLIS);
            }
            catch (InterruptedException ex)
            {
                // do nothing
            }
            catch (Exception | Error e)
            {
                e.printStackTrace(System.err);
            }
        }
    }

    private void dumpOne(
            ProcessControl<?, ?> pc)
    {
        int id = pc.processIdentifier();
        if (id > 0)
        {
            NuProcessBuilder bldr = new NuProcessBuilder(jstack.toString(),
                    Integer.toString(id));
            ProcessControl<String, String> ctrl = ProcessControl.create(bldr);
            ctrl.onExit().whenComplete(
                    (ProcessResult<String, String> result, Throwable thrown) ->
            {
                if (thrown != null)
                {
                    thrown.printStackTrace(System.err);
                    return;
                }
                // Use a lock to ensure we don't concurrently write output for multiple
                // processes
                synchronized (System.out)
                {
                    // Jstack can and will fail for various resons around the process
                    // exiting before or while it talks to it, so just ignore those.
                    if (result.isSuccess())
                    {
                        System.out.println(
                                "\n -------- Thread Dump for PID " + id + " --------");
                        System.out.println(result.standardOutput());

                        System.out.println(
                                "\n -------- Output for PID " + id + " --------");

                        System.out.println(pc.result().standardOutput());

                        String err = pc.result().standardError() + "";
                        if (!err.isEmpty())
                        {
                            System.out.println(
                                    "\n -------- Standard Error for PID " + id + " --------");
                            System.out.println(err);
                        }
                    }
                }
            });
            bldr.start();
        }
    }
}
