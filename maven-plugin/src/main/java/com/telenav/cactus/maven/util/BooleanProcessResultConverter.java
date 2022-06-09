package com.telenav.cactus.maven.util;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import static com.telenav.cactus.maven.util.CliCommand.completionStageForProcess;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Simple process result conversion when the only thing you care about is the
 * exit code.
 *
 * @author Tim Boudreau
 */
final class BooleanProcessResultConverter implements ProcessResultConverter<Boolean>
{

    private final IntPredicate exitCodeTest;

    BooleanProcessResultConverter(IntPredicate exitCodeTest)
    {
        this.exitCodeTest = exitCodeTest;
    }

    BooleanProcessResultConverter()
    {
        this(code -> code == 0);
    }

    @Override
    public AwaitableCompletionStage<Boolean> onProcessStarted(Supplier<String> supp, Process process)
    {
        return completionStageForProcess(process).thenApplyAsync(proc ->
        {
            return exitCodeTest.test(proc.exitValue());
        });
    }

}
