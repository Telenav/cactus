package com.telenav.cactus.process;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.currentTimeMillis;
import static java.lang.Thread.sleep;
import static java.util.Collections.synchronizedMap;

/**
 * Kills processes that persist longer than a per-process timeout.
 *
 * @author Tim Boudreau
 */
final class KillQueue
{

    private static final KillQueue INSTANCE = new KillQueue();
    private final Thread killThread = new Thread(this::killLoop,
            "Process timeout killer");
    private final Map<ProcessControl<?, ?>, Long> toKill = synchronizedMap(
            new WeakHashMap<>());
    private final AtomicBoolean started = new AtomicBoolean();

    private KillQueue()
    {
        killThread.setPriority(Thread.NORM_PRIORITY - 1);
        killThread.setDaemon(true);
    }

    static void enqueue(Duration after, ProcessControl<?, ?> ctrl)
    {
        INSTANCE._enqueue(after, ctrl);
    }

    private void _enqueue(Duration after, ProcessControl<?, ?> ctrl)
    {
        long expires = currentTimeMillis() + after.toMillis();
        toKill.put(ctrl, expires);
        if (started.compareAndSet(false, true))
        {
            killThread.start();
        }
        else
        {
            killThread.interrupt();
        }
    }

    private List<ProcessControl<?, ?>> removeTimedOut()
    {
        Set<ProcessControl<?, ?>> toRemove = new HashSet<>();
        List<ProcessControl<?, ?>> timedOut = new ArrayList<>();
        toKill.forEach((proc, expire) ->
        {
            if (!proc.state().isRunning())
            {
                toRemove.add(proc);
            }
            else
            {
                if (expire < currentTimeMillis())
                {
                    toRemove.add(proc);
                    timedOut.add(proc);
                }
            }
        });
        for (ProcessControl ctrl : toRemove)
        {
            toKill.remove(ctrl);
        }
        return timedOut;
    }

    private long leastInterval()
    {
        long result = Long.MAX_VALUE;
        for (Map.Entry<ProcessControl<?, ?>, Long> e : new HashSet<>(toKill.entrySet()))
        {
            long remaining = e.getValue() - currentTimeMillis();
            result = min(result, remaining);
            if (result < 0)
            {
                break;
            }
        }
        return max(0, result);
    }

    private void killLoop()
    {
        // Note, while a DelayQueue would be appropriate here, that would not
        // let us wake up more frequently and prune exited processes
        for (;;)
        {
            try
            {
                // Use a divisor so we can do some housekeeping
                sleep(leastInterval() / 4);
            }
            catch (InterruptedException ex)
            {
                // ok, that's how we wake up, so we recompute the sleep interval
            }
            removeTimedOut().forEach(this::killOne);
        }
    }

    private void killOne(ProcessControl<?, ?> ctrl)
    {
        if (ctrl.state().isRunning())
        {
            try
            {
                ctrl.kill();
                System.err.println("Killed " + ctrl);
            }
            catch (Exception | Error ex)
            {
                ex.printStackTrace(System.err);
            }
        }
    }

}
