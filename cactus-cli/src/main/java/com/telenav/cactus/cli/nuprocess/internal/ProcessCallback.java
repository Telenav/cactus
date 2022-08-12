package com.telenav.cactus.cli.nuprocess.internal;

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.telenav.cactus.cli.nuprocess.ProcessControl;
import com.telenav.cactus.cli.nuprocess.ProcessResult;
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

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author Tim Boudreau
 */
public final class ProcessCallback implements NuProcessHandler, ProcessControl
{
    private final CountDownLatch latch = new CountDownLatch(1);
    private final AtomicInteger exitCode = new AtomicInteger(-1);
    private final ConcurrentLinkedList<ProcessListener> listeners
            = ConcurrentLinkedList.fifo();
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();
    private StdinHandler stdin = StdinHandler.DEFAULT;
    private volatile boolean wantStdin;
    private NuProcess process;

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
        wantStdin = wantIn;
        if (process != null)
        {
            process.wantWrite();
        }
        return this;
    }

    @Override
    public int exitValue()
    {
        return exitCode.get();
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
                if (exitCode.compareAndSet(-1, Integer.MAX_VALUE))
                {
                    try
                    {
                        notifyListeners(Integer.MAX_VALUE);
                    }
                    finally
                    {
                        latch.countDown();
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean isRunning()
    {
        NuProcess proc;
        synchronized (this)
        {
            proc = process;
        }
        if (proc != null)
        {
            return proc.isRunning();
        }
        else
        {
            return false;
        }
    }

    public synchronized StdinHandler stdin()
    {
        return stdin;
    }

    public void onExit(CompletableFuture<ProcessResult> future)
    {
        listen((exitCode, stdin, stdout) ->
        {
            future.complete(new ProcessResult(exitCode, stdin, stdout));
        });
    }

    public void listen(ProcessListener l)
    {
        listeners.push(l);
        int code = exitCode.get();
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
        List<ProcessListener> all = new ArrayList<>();
        try
        {
            listeners.drain(listener ->
            {
                listener.processExited(exitCode, stdout, stderr);
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
        if (exitCode.compareAndSet(-1, exit))
        {
            notifyListeners(exit);
            latch.countDown();
        }
    }

    @Override
    public synchronized void onPreStart(NuProcess np)
    {
        process = np;
    }

    @Override
    public synchronized void onStart(NuProcess np)
    {
        process = np;
        if (wantStdin)
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
        return new ProcessResult(exitCode.get(), stdout, stderr);
    }

}
