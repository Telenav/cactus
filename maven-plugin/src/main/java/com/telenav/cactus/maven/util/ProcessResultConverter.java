package com.telenav.cactus.maven.util;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

/**
 * Parses output and exit code from a command into a result object.
 *
 * @param <T>
 */
public interface ProcessResultConverter<T>
{

    AwaitableCompletionStage<T> onProcessStarted(Supplier<String> description, Process process);

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
        return (description, proc) ->
        {
            return AwaitableCompletionStage.of(
                    onProcessStarted(description, proc)
                            .thenApply(converter));
        };
    }
}
