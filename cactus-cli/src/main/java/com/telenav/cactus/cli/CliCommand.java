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
import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.util.PathUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * A utility program we need to run, which takes care of the general ugliness of
 * java process management, and converting output into a usable object.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
public abstract class CliCommand<T> implements Supplier<String>
{
    public static AwaitableCompletionStage<Process> completionStageForProcess(
            Process proc)
    {
        return AwaitableCompletionStage.of(proc.onExit());
    }

    public static CliCommand<String> fixed(String command, Path workingDir,
            String... fixedArgs)
    {
        return new SimpleCommand(command, workingDir, fixedArgs);
    }

    static class SimpleCommand extends CliCommand<String>
    {
        private final Path workingDir;

        private final String[] fixedArgs;

        public SimpleCommand(String name, Path workingDir, String... fixedArgs)
        {
            super(name, new StringProcessResultConverterImpl());
            this.workingDir = workingDir;
            this.fixedArgs = fixedArgs;
        }

        @Override
        protected void configureArguments(List<String> list)
        {
            list.addAll(Arrays.asList(fixedArgs));
        }

        @Override
        protected Optional<Path> workingDirectory()
        {
            return Optional.ofNullable(workingDir);
        }
    }

    protected final String name;

    protected final ProcessResultConverter<T> resultCreator;

    public CliCommand(String name, ProcessResultConverter<T> resultCreator)
    {
        this.name = name;
        this.resultCreator = resultCreator;
    }

    public String get()
    {
        return toString();
    }

    public AwaitableCompletionStage<T> run()
    {
        return AwaitableCompletionStage.from(() ->
        {
            ThrowingOptional<Process> p = launch();
            if (!p.isPresent())
            {
                return CompletableFuture.failedStage(
                        new IOException("Could not find executable for " + this));
            }
            return resultCreator.onProcessStarted(this, p.get());
        });
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(name);
        List<String> args = new ArrayList<>();
        configureArguments(args);
        for (String arg : args)
        {
            sb.append(' ').append(arg);
        }
        workingDirectory().ifPresent(dir
                -> sb.append(" (in ").append(dir).append(')'));
        return sb.toString();
    }

    /**
     * Append command-line arguments passed to the found binary to the list.
     *
     * @param list A list
     */
    protected abstract void configureArguments(List<String> list);

    /**
     * Do any customization of the process builder (env, etc) here.
     *
     * @param bldr A process builder
     */
    protected void configureProcessBulder(ProcessBuilder bldr)
    {
        // for subclasses
    }

    protected ThrowingOptional<Process> launch()
    {
        validate();
        return ThrowingOptional.from(PathUtils.findExecutable(name)).map(path ->
        {
            List<String> commandLine = new ArrayList<>();
            commandLine.add(path.toString());
            configureArguments(commandLine);
            ProcessBuilder pb = new ProcessBuilder(commandLine);
            internalConfigureProcessBuilder(pb);
            Process proc = pb.start();
            onLaunch(proc);
            return proc;
        });
    }

    /**
     * Override to log process start or similar.
     *
     * @param proc A process
     */
    protected void onLaunch(Process proc)
    {
    }

    /**
     * Throw here if the command is misconfigured and cannot be run.
     */
    protected void validate()
    {
        // do nothing
    }

    protected Optional<Path> workingDirectory()
    {
        return Optional.empty();
    }

    private void internalConfigureProcessBuilder(ProcessBuilder bldr)
    {
        workingDirectory().ifPresent(dir ->
        {
            bldr.directory(dir.toFile());
        });
        configureProcessBulder(bldr);
    }
}
