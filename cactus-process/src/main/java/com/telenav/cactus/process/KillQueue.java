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
package com.telenav.cactus.process;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.lang.Math.max;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Kills processes that persist longer than a per-process timeout.
 *
 * @author Tim Boudreau
 */
final class KillQueue
{

    private static final KillQueue INSTANCE = new KillQueue();
    private final DelayQueue<KillQueueEntry> toKill = new DelayQueue<>();
    private final AtomicBoolean started = new AtomicBoolean();
    // So tests can verify processes get killed
    static Consumer<ProcessControl<?, ?>> VERIFIER = ignored ->
    {
    };

    private KillQueue()
    {
    }

    static void enqueue(Duration after, ProcessControl<?, ?> ctrl)
    {
        INSTANCE._enqueue(after, ctrl);
    }

    static boolean isStarted()
    {
        // for tests
        return INSTANCE.started.get();
    }

    private void _enqueue(Duration after, ProcessControl<?, ?> ctrl)
    {
        long expires = currentTimeMillis() + after.toMillis();
        toKill.offer(new KillQueueEntry(ctrl, expires));
        if (started.compareAndSet(false, true))
        {
            Thread killThread = new Thread(this::killLoop,
                    "Process timeout killer");
            killThread.setPriority(Thread.NORM_PRIORITY - 1);
            killThread.setDaemon(true);
            killThread.start();
        }
    }

    private void killLoop()
    {
        // Note, while a DelayQueue would be appropriate here, that would not
        // let us wake up more frequently and prune exited processes
        try
        {
            for (;;)
            {
                try
                {
                    toKill.take().kill();
                }
                catch (Exception | Error ex)
                {
                    if (!(ex instanceof InterruptedException))
                    {
                        // Hmm, is this still ever used with the removal of
                        // Thread.stop()?
                        if (ex instanceof ThreadDeath)
                        {
                            throw ((ThreadDeath) ex);
                        }
                        ex.printStackTrace();
                    }
                }
            }
        }
        finally
        {
            // If we did exit for some unusual reason, the next call
            // to enqueue will start a new kill thread.
            started.set(false);
        }
    }

    @SuppressWarnings("UnusedReturnValue") private static final class KillQueueEntry implements Delayed
    {
        private final Reference<ProcessControl<?, ?>> processRef;
        private final long expiresAt;

        public KillQueueEntry(
                ProcessControl<?, ?> process, long expiresAt)
        {
            this.processRef = new WeakReference<>(process);
            this.expiresAt = expiresAt;
        }

        boolean isDead()
        {
            return processRef.get() == null;
        }

        boolean kill()
        {
            ProcessControl<?, ?> process = processRef.get();
            if (process != null)
            {
                VERIFIER.accept(process);
                return process.kill();
            }
            return false;
        }

        @SuppressWarnings("NullableProblems")
        @Override
        public long getDelay(TimeUnit unit)
        {
            if (isDead())
            {
                return 0;
            }
            return unit.convert(max(0, expiresAt - currentTimeMillis()),
                    MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o)
        {
            // Why oh why does Delayed not add a default method for this?
            return Long
                    .compare(getDelay(MILLISECONDS), o.getDelay(MILLISECONDS));
        }
    }

}
