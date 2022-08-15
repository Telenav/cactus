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
package com.telenav.cactus.test.project.generator;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.cli.CliCommand;
import com.telenav.cactus.cli.ProcessResultConverter;
import com.telenav.cactus.maven.log.BuildLog;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static com.mastfrog.util.preconditions.Exceptions.chuck;
import static com.telenav.cactus.cli.CliCommand.completionStageForProcess;
import static com.telenav.cactus.cli.ProcessResultConverter.exitCodeIsZero;
import static java.lang.System.getenv;
import static java.lang.ThreadLocal.withInitial;
import static java.nio.charset.Charset.defaultCharset;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isExecutable;
import static java.util.Arrays.asList;

/**
 *
 * @author Tim Boudreau
 */
public final class MavenCommand extends CliCommand<Boolean>
{
    private final BuildLog log;
    private final String[] args;
    private final Path dir;

    public MavenCommand(Path dir, String... args)
    {
        super(mvn(), converter(BuildLog.get().child(findLogName(args))));
        log = BuildLog.get().child(findLogName(args));
        this.dir = dir;
        this.args = args;
    }

    private static String findLogName(String... args)
    {
        // Take the name from the maven task
        String s = args[args.length - 1];
        int ix = s.lastIndexOf(':');
        if (ix >= 0)
        {
            s = s.substring(ix + 1);
        }
        return s;
    }

    @Override
    protected Optional<Path> workingDirectory()
    {
        return Optional.of(dir);
    }

    @Override
    protected void onLaunch(Process proc)
    {
        if (DEBUG.get())
        {
            System.out.println(this);
        }
        super.onLaunch(proc);
    }

    private static String mvn()
    {
        String pth = getenv("M2_HOME");
        if (pth != null)
        {
            Path bin = Paths.get(pth, "bin/mvn");
            if (exists(bin) && isExecutable(bin))
            {
                return bin.toString();
            }
        }
        return "mvn";
    }

    @Override
    protected void configureArguments(List<String> list)
    {
        if (DEBUG.get())
        {
            list.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=debug");
        }
        list.addAll(asList(args));
    }

    static ProcessResultConverter<Boolean> converter(BuildLog log)
    {
        return DEBUG.get()
               ? new DebugProcessResultConverter(log)
               : exitCodeIsZero();
    }

    private static final ThreadLocal<Boolean> DEBUG
            = withInitial(() -> false);

    // We do want a thread-safe list, because we will be adding to the
    // list from the process callback thread, not the thread that will try
    // to fetch and use the output
    private static final ThreadLocal<List<String>> OUTPUT
            = ThreadLocal.withInitial(CopyOnWriteArrayList::new);

    /**
     * If in the closure of a call to debug(), stdout of each maven call is
     * saved, and the output of tasks called within that closure can be obtained
     * here (0 is the most recently run task).
     *
     * @param index
     * @return
     */
    public static String debugOutput(int index)
    {
        List<String> list = OUTPUT.get();
        if (index >= 0 && index < list.size())
        {
            return list.get(index);
        }
        return null;
    }

    /**
     * Any MavenCommands instantiated within the passed lambda will use an
     * alternate output converter which saves output so it can be retrieved from
     * debugOutput(), and may be logged.
     *
     * @param run A runnable
     */
    public static void debug(ThrowingRunnable run)
    {
        boolean old = DEBUG.get();
        try
        {
            DEBUG.set(true);
            run.toNonThrowing().run();
        }
        finally
        {
            DEBUG.set(old);
            OUTPUT.get().clear();
        }
    }

    static final class DebugProcessResultConverter implements
            ProcessResultConverter<Boolean>
    {
        final IntPredicate exitCodeTest;
        private volatile OutputReader stderr;
        private volatile OutputReader stdout;
        private final BuildLog log;
        // Need to grab this on the originating thread - we will be on a
        // background thread when we require the output
        private final List<String> appendOutputTo = OUTPUT.get();

        DebugProcessResultConverter(IntPredicate exitCodeTest)
        {
            this.exitCodeTest = exitCodeTest;
            this.log = BuildLog.get();
        }

        DebugProcessResultConverter(BuildLog log)
        {
            this.exitCodeTest = code -> code == 0;
            this.log = log;
        }

        @Override
        public AwaitableCompletionStage<Boolean> onProcessStarted(
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
                    return "exit " + proc.exitValue() + ":\n" + stdout
                            .toString() + "\n"
                            + (proc.exitValue() != 0
                               ? stderr.toString()
                               : "");
                });
                String out = stdout.done();
                log.debug(() -> out);
                appendOutputTo.add(0, out);
                String err = stderr.done();
                log.debug(() -> err);
                System.out.println("OUTPUT:\n" + out);
                System.out.println("ERR:\n" + err);
                System.out.println("EXIT " + proc.exitValue());
                return exitCodeTest.test(proc.exitValue());
            });
        }

        private static class OutputReader implements Runnable
        {

            private final BufferedReader in;
            private final StringBuilder sb = new StringBuilder();
            private final Thread thread = new Thread(this,
                    "process-output-reader");
            private volatile Throwable thrown;

            // Sigh... 27 years of Java and the API for interacting with processes
            // is still goshawful.
            OutputReader(InputStream in)
            {
                this.in = new BufferedReader(new InputStreamReader(in,
                        defaultCharset()), 512);
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
                    return chuck(t);
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
                char[] buf = new char[1_024];
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
}
