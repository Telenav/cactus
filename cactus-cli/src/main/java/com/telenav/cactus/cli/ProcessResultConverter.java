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
import java.net.URI;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static java.lang.Thread.currentThread;

/**
 * Parses output and exit code from a command into a result object.
 *
 * @param <T>
 */
public interface ProcessResultConverter<T>
{

    AwaitableCompletionStage<T> onProcessStarted(Supplier<String> description,
            Process process);

    public static StringProcessResultConverter strings()
    {
        return new StringProcessResultConverterImpl();
    }

    public static StringProcessResultConverter strings(
            IntPredicate exitCodeTester)
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

    public static ProcessResultConverter<Integer> rawExitCode()
    {
        return (description, proc) ->
        {
            return AwaitableCompletionStage.of(proc.onExit().handle(
                    (p, thrown) ->
            {
                return thrown != null
                       ? -1
                       : p.exitValue();
            }));
        };
    }

    public static ProcessResultConverter<URI> trailingUriWithTrailingDigitAloneOnLine()
    {
        return strings().map(processOutput ->
        {
            String[] lines = processOutput.split("\n");
            for (int i = lines.length - 1; i >= 0; i--)
            {
                String ln = lines[i].trim();
                // A github pull request url ends in at least one digit
                if (ln.startsWith("https://") && Character.isDigit(ln.charAt(ln
                        .length() - 1)))
                {
                    return URI.create(ln);
                }
            }
            throw new IllegalArgumentException(
                    "No URI found in process output \n'"
                    + processOutput + "'");
        });
    }

    default <R> ProcessResultConverter<R> map(Function<T, R> converter)
    {
        // Get the maven classloader and apply it on the background thread
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return (description, proc) ->
        {
            return AwaitableCompletionStage.of(
                    onProcessStarted(description, proc)
                            .thenApply(arg ->
                            {
                                Thread t = currentThread();
                                ClassLoader old = t.getContextClassLoader();
                                try
                                {
                                    t.setContextClassLoader(classLoader);
                                    return converter.apply(arg);
                                }
                                finally
                                {
                                    t.setContextClassLoader(old);
                                }
                            }));
        };
    }
}
