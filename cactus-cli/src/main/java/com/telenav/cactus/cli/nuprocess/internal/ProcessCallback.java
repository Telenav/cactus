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
package com.telenav.cactus.cli.nuprocess.internal;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.cli.nuprocess.ProcessControl;
import com.telenav.cactus.cli.nuprocess.ProcessResult;
import com.telenav.cactus.cli.nuprocess.ProcessState;
import com.telenav.cactus.cli.nuprocess.StdinHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessHandler;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.RUNNING;
import static com.telenav.cactus.cli.nuprocess.ProcessState.RunningStatus.STARTING;
import static com.telenav.cactus.cli.nuprocess.ProcessState.processState;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * NuProcessHandler implementation which implements ProcessControl.
 *
 * @author Tim Boudreau
 */
public final class ProcessCallback implements NuProcessHandler, ProcessControl
{
    // This AtomicInteger holds the running state, the exit code and
    // a few other state bits
    private final AtomicInteger state = new AtomicInteger();
    private final CountDownLatch latch = new CountDownLatch(1);
    private final ConcurrentLinkedList<ProcessListener> listeners
            = ConcurrentLinkedList.fifo();
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private StdinHandler stdin = StdinHandler.DEFAULT;
    private NuProcess process;

    private ProcessState updateAndGetState(
            UnaryOperator<ProcessState> transition)
    {
        int result = state.updateAndGet(old ->
        {
            return transition.apply(processState(old)).intValue();
        });
        return processState(result);
    }

    private ProcessState getAndUpdateState(
            UnaryOperator<ProcessState> transition)
    {
        int result = state.getAndUpdate(old ->
        {
            return transition.apply(processState(old)).intValue();
        });
        return processState(result);
    }

    public ProcessState state()
    {
        return processState(state.get());
    }

    @Override
    public synchronized ProcessCallback withStdinHandler(StdinHandler handler,
            boolean wantIn)
    {
        if (isRunning())
        {
            throw new IllegalStateException("Cannot set stdin handler"
                    + "after process launch");
        }
        this.stdin = notNull("handler", handler);
        ProcessState prev = getAndUpdateState(old -> old.wantingInput());
        if (process != null && !prev.wantsInput())
        {
            process.wantWrite();
        }
        return this;
    }

    @Override
    public int exitValue()
    {
        return state().exitCode();
    }

    @Override
    public boolean kill()
    {
        NuProcess proc;
        synchronized (this)
        {
            proc = process;
        }
        if (proc == null)
        {
            return false;
        }
        if (proc.isRunning())
        {
            try
            {
                proc.destroy(true);
                return true;
            }
            finally
            {
                getAndUpdateState(old -> old.killed());
            }
        }
        return false;
    }

    @Override
    public boolean isRunning()
    {
        return state().isRunning();
    }

    public synchronized StdinHandler stdin()
    {
        return stdin;
    }

    public void onExit(CompletableFuture<ProcessResult> future)
    {
        listen((exitCode, stdin, stdout) ->
        {
            future.complete(new ProcessResult(state(), stdin, stdout));
        });
    }

    public void listen(ProcessListener l)
    {
        listeners.push(l);
        ProcessState state = state();
        int code = state.wasKilled()
                   ? Integer.MAX_VALUE
                   : state.isExited()
                     ? state.exitCode()
                     : -1;
        if (code >= 0)
        {
            notifyListeners(code);
        }
    }

    @Override
    public void await() throws InterruptedException
    {
        latch.await();
    }

    @Override
    public void await(Duration dur) throws InterruptedException
    {
        latch.await(dur.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void notifyListeners(int exitCode)
    {
        ProcessState state = state().withExitCode(exitCode);
        List<ProcessListener> all = new ArrayList<>();
        try
        {
            // This is atomic and ensures we cannot invoke a listener
            // twice
            listeners.drain(listener ->
            {
                listener.processExited(state, stdout, stderr);
            });
        }
        finally
        {
            latch.countDown();
        }
    }

    @Override
    public void onExit(int exit)
    {
        ProcessState old = getAndUpdateState(oldState
                -> oldState.withExitCode(exit));
        if (old.exitCode() == 0 && old.isRunning())
        {
            notifyListeners(exit);
            latch.countDown();
        }
    }

    @Override
    public void onPreStart(NuProcess np)
    {
        synchronized (this)
        {
            process = notNull("np", np);
        }
        updateAndGetState(old -> old.toState(STARTING));
    }

    @Override
    public void onStart(NuProcess np)
    {
        synchronized (this)
        {
            process = notNull("np", np);
        }
        if (state().wasKilled())
        {
            np.destroy(true);
        }
        boolean wantWrite = updateAndGetState(old -> old.toState(RUNNING))
                .wantsInput();
        if (wantWrite)
        {
            np.wantWrite();
        }
    }

    private String append(ByteBuffer bb, StringBuilder into)
    {
        byte[] bytes = new byte[bb.remaining()];
        bb.get(bytes);
        String s = new String(bytes, UTF_8);
        synchronized (into)
        {
            // Paranoid, perhaps, but a read access from another thread
            // can be a cache miss otherwise.
            into.append(s);
        }
        return s;
    }

    @Override
    public void onStdout(ByteBuffer bb, boolean bln)
    {
        append(bb, stdout);
    }

    @Override
    public void onStderr(ByteBuffer bb, boolean bln)
    {
        append(bb, stderr);
    }

    @Override
    public boolean onStdinReady(ByteBuffer bb)
    {
        return stdin().onStdinReady(this, bb);
    }

    @Override
    public ProcessResult result()
    {
        return new ProcessResult(state(), stdout, stderr);
    }

    @Override
    public String toString()
    {
        NuProcess proc;
        synchronized (this)
        {
            proc = process;
        }
        return proc.toString() + " " + state();
    }

}
