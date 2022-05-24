package com.telenav.cactus.maven.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A utility program we need to run, which takes care of the general ugliness of
 * java process management, and converting output into a usable object.
 *
 * @author Tim Boudreau
 */
public abstract class CliCommand<T>
{

    protected final String name;
    protected final ProcessResultConverter<T> resultCreator;

    public CliCommand(String name, ProcessResultConverter<T> resultCreator)
    {
        this.name = name;
        this.resultCreator = resultCreator;
    }

    public static CliCommand<String> fixed(String command, Path workingDir, String... fixedArgs)
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
        protected Optional<Path> workingDirectory()
        {
            return Optional.ofNullable(workingDir);
        }

        @Override
        protected void configureArguments(List<String> list)
        {
            list.addAll(Arrays.asList(fixedArgs));
        }
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
        workingDirectory().ifPresent(dir ->
        {
            sb.append(" (in ").append(dir).append(')');
        });
        return sb.toString();
    }

    protected Optional<Path> workingDirectory()
    {
        return Optional.empty();
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

    /**
     * Throw here if the command is misconfigured and cannot be run.
     */
    protected void validate()
    {
        // do nothing
    }

    private void internalConfigureProcessBuilder(ProcessBuilder bldr)
    {
        workingDirectory().ifPresent(dir ->
        {
            bldr.directory(dir.toFile());
        });
        configureProcessBulder(bldr);
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
            return resultCreator.onProcessStarted(p.get());
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
}
