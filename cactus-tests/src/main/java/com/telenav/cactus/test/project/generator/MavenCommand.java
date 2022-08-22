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
import com.telenav.cactus.process.ProcessControl;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.util.PathUtils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.cli.CliCommand.completionStageForProcess;
import static com.telenav.cactus.cli.ProcessResultConverter.exitCodeIsZero;
import static java.lang.System.getenv;
import static java.lang.ThreadLocal.withInitial;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.isExecutable;
import static java.util.Arrays.asList;

/**
 *
 * @author Tim Boudreau
 */
public final class MavenCommand extends CliCommand<Boolean>
{
    private static String maven;
    private final BuildLog log;
    private final String[] args;
    private final Path dir;

    public MavenCommand(Path dir, String... args)
    {
        super(mvn(), converter(BuildLog.get().child(findLogName(args))));
        log = BuildLog.get().child(findLogName(args));
        this.dir = notNull("dir", dir);
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
    protected void onLaunch(ProcessControl<String, String> proc)
    {
        if (DEBUG.get())
        {
            System.out.println(this);
        }
        log.debug(() -> "Run maven: " + this + " gets process " + proc);
    }

    private static String mvn()
    {
        if (maven != null)
        {
            return maven;
        }
        String pth = getenv("M2_HOME");
        if (pth != null)
        {
            Path bin = Paths.get(pth, "bin/mvn");
            if (exists(bin) && isExecutable(bin))
            {
                maven = bin.toString();
            }
        }
        if (maven == null)
        {
            maven = PathUtils.findExecutable("mvn").map(mvn -> mvn.toString())
                    .orElse("mvn");
        }
        System.out.println("Maven location is " + maven);
        return maven;
    }

    @Override
    protected void configureArguments(List<String> list)
    {
        if (DEBUG.get())
        {
            list.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=debug");
        }
        list.add("--no-transfer-progress");
        list.add("--batch-mode");
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
                Supplier<String> description,
                ProcessControl<String, String> process)
        {
            // Note:  This really needs to be thenApplyAsync(), or you sometimes get
            // immediately called back before the process has *started*.
            return completionStageForProcess(process).thenApply(result ->
            {
                log.debug(()
                        -> "exit " + result.exitValue() + ":\n" + result
                        .standardOutput() + "\n"
                        + (result.exitValue() != 0
                           ? result.standardError()
                           : "")
                );
                String out = result.standardOutput();
                log.debug(() -> out);
                appendOutputTo.add(0, out);
                log.debug(() -> result.standardError());
                System.out.println("OUTPUT:\n" + out);
                System.out.println("ERR:\n" + result.standardError());
                System.out.println("EXIT " + result.exitValue());

                return exitCodeTest.test(result.exitValue());
            });
        }

    }
}
