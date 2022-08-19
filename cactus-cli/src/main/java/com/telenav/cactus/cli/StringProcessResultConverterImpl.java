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
package com.telenav.cactus.cli;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.log.BuildLog;

import static com.telenav.cactus.cli.CliCommand.completionStageForProcess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 *
 * @author Tim Boudreau
 */
final class StringProcessResultConverterImpl implements
        StringProcessResultConverter
{

    final IntPredicate exitCodeTest;
    private volatile OutputReader stderr;
    private volatile OutputReader stdout;
    private final BuildLog log = BuildLog.get();

    public StringProcessResultConverterImpl(IntPredicate exitCodeTest)
    {
        this.exitCodeTest = exitCodeTest;
    }

    public StringProcessResultConverterImpl()
    {
        this(code -> code == 0);
    }

    @Override
    public AwaitableCompletionStage<String> onProcessStarted(
            Supplier<String> description, Process process)
    {
        stderr = new OutputReader(process.getErrorStream()).start();
        stdout = new OutputReader(process.getInputStream()).start();
        // Note:  This really needs to be thenApplyAsync(), or you sometimes get
        // immediately called back before the process has *started*.
        return completionStageForProcess(process).thenApply(proc ->
        {
            log.debug(() ->
            {
                return "exit " + proc.exitValue() + ":\n" + stdout.toString() + "\n"
                        + (proc.exitValue() != 0
                           ? stderr.toString()
                           : "");
            });
            if (exitCodeTest.test(proc.exitValue()))
            {
                stderr.done();
                return stdout.done();
            }
            throw new ProcessFailedException(description, process, stdout.done(),
                    stderr.done());
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
            this.in = new BufferedReader(new InputStreamReader(in, Charset
                    .defaultCharset()), 512);
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
            if (t != null)
            {
                // unlikely but cover all the bases
                return Exceptions.chuck(t);
            }
            try
            {
                thread.join();
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
            }
            run();
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
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
                thrown = ex;
            }
        }
    }
}
