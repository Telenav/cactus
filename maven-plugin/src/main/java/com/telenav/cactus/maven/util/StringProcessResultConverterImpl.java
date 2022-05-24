package com.telenav.cactus.maven.util;

import com.mastfrog.util.preconditions.Exceptions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.IntPredicate;

/**
 *
 * @author Tim Boudreau
 */
final class StringProcessResultConverterImpl implements StringProcessResultConverter
{

    final IntPredicate exitCodeTest;
    private volatile OutputReader stderr;
    private volatile OutputReader stdout;

    public StringProcessResultConverterImpl(IntPredicate exitCodeTest)
    {
        this.exitCodeTest = exitCodeTest;
    }

    public StringProcessResultConverterImpl()
    {
        this(code -> code == 0);
    }

    @Override
    public AwaitableCompletionStage<String> onProcessStarted(Process process)
    {
        stderr = new OutputReader(process.getErrorStream()).start();
        stdout = new OutputReader(process.getInputStream()).start();
        // Note:  This really needs to be thenApplyAsync(), or you sometimes get
        // immediately called back before the process has *started*.
        return AwaitableCompletionStage.of(process).thenApplyAsync(proc ->
        {
            if (exitCodeTest.test(proc.exitValue()))
            {
                return stdout.done();
            }
            throw new ProcessFailedException(process, stdout.done(), stderr.done());
        });
    }

    private static class OutputReader implements Runnable
    {

        private final BufferedReader in;
        private final StringBuilder sb = new StringBuilder();
        private final Thread thread = new Thread(this, "process-output-reader");
        private volatile Throwable thrown;

        // Sigh... 27 years of Java and the API for interacting with processes
        // is still goshawful.
        public OutputReader(InputStream in)
        {
            this.in = new BufferedReader(new InputStreamReader(in, Charset.defaultCharset()), 512);
        }

        @Override
        public String toString()
        {
            synchronized (this)
            {
                return sb.toString();
            }
        }

        public String done()
        {
            thread.interrupt();
            Throwable t = thrown;
            if (t != null) {
                // unlikely but cover all the bases
                return Exceptions.chuck(t);
            }
            return toString();
        }

        public OutputReader start()
        {
            thread.start();
            return this;
        }

        @Override
        public void run()
        {
            char[] buf = new char[1024];
            int count;
            try
            {
                while ((count = in.read(buf)) != -1)
                {
                    synchronized (this)
                    {
                        sb.append(buf, 0, count);
                    }
                }
            } catch (IOException ex)
            {
                ex.printStackTrace();
                thrown = ex;
            }
        }
    }
}
