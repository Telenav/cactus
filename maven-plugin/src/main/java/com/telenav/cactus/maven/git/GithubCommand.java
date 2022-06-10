package com.telenav.cactus.maven.git;

import com.mastfrog.util.preconditions.Checks;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.util.CliCommand;
import com.telenav.cactus.maven.util.ProcessResultConverter;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
public class GithubCommand<T> extends CliCommand<T>
{
    private final Path workingDir;

    private final String[] args;

    private final BuildLog log = BuildLog.get().child(getClass().getSimpleName());

    public GithubCommand(ProcessResultConverter<T> resultCreator, String... args)
    {
        this(resultCreator, null, args);
    }

    public GithubCommand(ProcessResultConverter<T> resultCreator, Path workingDir, String... args)
    {
        super("gh", resultCreator);
        this.workingDir = workingDir;
        this.args = Checks.notNull("args", args);
    }

    public GithubCommand<T> withWorkingDir(Path dir)
    {
        return new GithubCommand<>(resultCreator, dir, args);
    }

    @Override
    protected void configureArguments(List<String> list)
    {
        list.addAll(Arrays.asList(args));
    }

    @Override
    protected void onLaunch(Process proc)
    {
        log.debug(() -> "started: " + this);
        super.onLaunch(proc);
    }

    @Override
    protected void validate()
    {
        if (workingDir == null)
        {
            throw new IllegalStateException("Command is a template. Use "
                    + "withWorkingDir() to get an instance that has "
                    + "somewhere to run.");
        }
    }

    @Override
    protected Optional<Path> workingDirectory()
    {
        return Optional.ofNullable(workingDir);
    }
}
