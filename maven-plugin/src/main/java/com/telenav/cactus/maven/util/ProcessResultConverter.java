package com.telenav.cactus.maven.util;

import java.util.function.Function;
import java.util.function.IntPredicate;

/**
 * Parses output and exit code from a command into a result object.
 *
 * @param <T>
 */
public interface ProcessResultConverter<T>
{

    AwaitableCompletionStage<T> onProcessStarted(Process process);

    public static StringProcessResultConverter strings()
    {
        return new StringProcessResultConverterImpl();
    }

    public static StringProcessResultConverter strings(IntPredicate exitCodeTester)
    {
        return new StringProcessResultConverterImpl(exitCodeTester);
    }

    public static ProcessResultConverter<Boolean> exitCodeIsZero()
    {
        return new BooleanProcessResultConverter();
    }

    public static ProcessResultConverter<Boolean> exitCode(IntPredicate pred)
    {
        return new BooleanProcessResultConverter(pred);
    }

    default <R> ProcessResultConverter<R> map(Function<T, R> converter)
    {
        return proc ->
        {
            return AwaitableCompletionStage.of(ProcessResultConverter.this.onProcessStarted(proc)
                    .thenApply(converter));
        };
    }
}
