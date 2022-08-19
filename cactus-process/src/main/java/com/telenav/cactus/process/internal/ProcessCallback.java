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
package com.telenav.cactus.process.internal;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.process.OutputHandler;
import com.telenav.cactus.process.ProcessControl;
import com.telenav.cactus.process.ProcessResult;
import com.telenav.cactus.process.ProcessState;
import com.telenav.cactus.process.StandardInputHandler;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessHandler;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.process.ProcessState.RunningStatus.RUNNING;
import static com.telenav.cactus.process.ProcessState.RunningStatus.STARTING;
import static com.telenav.cactus.process.ProcessState.processState;

/**
 * NuProcessHandler implementation which implements ProcessControl; note that
 * this package is hidden by module-info.java - it should not be directly
 * touched by users of this library. Use ProcessControl.create() to create an
 * initial ProcessCallback and then configure to taste.
 *
 * @author Tim Boudreau
 * @param <O> The type standard output will be parsed to
 * @param <E> The type standard error will be parsed to
 */
public final class ProcessCallback<O, E> implements NuProcessHandler,
                                                    ProcessControl<O, E>
{
    // This AtomicInteger holds the running state, the exit code and
    // a few other state bits
    private final AtomicInteger state;
    private final CountDownLatch latch;
    private final ConcurrentLinkedList<ProcessListener> listeners;
    private final OutputHandler<O> stdout;
    private final OutputHandler<E> stderr;
    private StandardInputHandler stdin = StandardInputHandler.DEFAULT;
    private NuProcess process;

    ProcessCallback(OutputHandler<O> stdout, OutputHandler<E> stderr)
    {
        this.stdout = stdout;
        this.stderr = stderr;
        state = new AtomicInteger();
        latch = new CountDownLatch(1);
        listeners = ConcurrentLinkedList.fifo();
    }

    <OO, EE> ProcessCallback(ProcessCallback<OO, EE> orig,
            OutputHandler<O> stdout, OutputHandler<E> stderr)
    {
        this.stdout = stdout;
        this.stderr = stderr;
        this.state = orig.state;
        this.latch = orig.latch;
        this.listeners = orig.listeners;
        this.stdin = orig.stdin;
        this.process = orig.process;
    }

    @Override
    public <T> ProcessControl<O, T> withErrorHandler(OutputHandler<T> oe)
    {
        ProcessState.RunningStatus runState = state().state();
        switch (runState)
        {
            case UNINITIALIZED:
                break;
            default:
                throw new IllegalStateException(
                        "Cannot replace error handler in state " + runState);
        }
        return new ProcessCallback<>(this, stdout, oe);
    }

    @Override
    public <T> ProcessControl<T, E> withOutputHandler(OutputHandler<T> oh)
    {
        ProcessState.RunningStatus runState = state().state();
        switch (runState)
        {
            case UNINITIALIZED:
                break;
            default:
                throw new IllegalStateException(
                        "Cannot replace error handler in state " + runState);
        }
        return new ProcessCallback<>(this, oh, stderr);
    }

    public static <O, E> ProcessCallback<O, E> create(OutputHandler<O> output,
            OutputHandler<E> error)
    {
        return new ProcessCallback<>(output, error);
    }

    public static <O> ProcessCallback<O, Void> create(OutputHandler<O> output)
    {
        return new ProcessCallback<>(output, OutputHandler.NULL);
    }

    public static ProcessCallback<String, String> create()
    {
        return new ProcessCallback<>(OutputHandler.string(), OutputHandler
                .string());
    }

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

    @Override
    public ProcessState state()
    {
        return processState(state.get());
    }

    @Override
    public synchronized ProcessCallback<O, E> withStandardInputHandler(
            StandardInputHandler handler,
            boolean wantIn)
    {
        if (isRunning())
        {
            throw new IllegalStateException("Cannot set StandardInputHandler "
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

    public synchronized StandardInputHandler stdin()
    {
        return stdin;
    }

    public void onExit(CompletableFuture<ProcessResult<O, E>> future)
    {
        listen((exitCode) ->
        {
            future.complete(result());
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
        try
        {
            // This is atomic and ensures we cannot invoke a listener
            // twice
            listeners.drain(listener ->
            {
                listener.processExited(state);
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

    @Override
    public void onStdout(ByteBuffer bb, boolean bln)
    {
        stdout.onOutput(this, bb, bln);
    }

    @Override
    public void onStderr(ByteBuffer bb, boolean bln)
    {
        stderr.onOutput(this, bb, bln);
    }

    @Override
    public boolean onStdinReady(ByteBuffer bb)
    {
        return stdin().onStdinReady(this, bb);
    }

    @Override
    public ProcessResult<O, E> result()
    {
        return ProcessResult.create(state(), stdout.result(), stderr.result());
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
