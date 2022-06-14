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
