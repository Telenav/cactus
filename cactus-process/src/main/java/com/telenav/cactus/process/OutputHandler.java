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
package com.telenav.cactus.process;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * Callback which gets passed input (either stdin or stderr), and can collect it
 * in some fashion and parse/save/turn it into a result object of some type
 * useful to the caller.
 * <p>
 * Note: Implementations should be prepared for their OutputHandlers to be
 * called <i>before or while the process is running</i>, as
 * <code>result()</code> can be invoked at any time by a caller of
 * {@link ProcessControl$result}.
 * </p>
 *
 * @author Tim Boudreau
 * @param <T> The type output is concatenated, parsed or aggregated into
 */
public interface OutputHandler<T>
{
    /**
     * Called repeatedly when output is available for reading; the passed byte
     * buffer's position must be advanced by the amount read within the closure
     * of this method. The output buffer may be resed, and references to it
     * should not be retained. This method must be thread-safe with respect to
     * calls to the <code>result()</code> method.
     *
     * @param process the process control - some implementations that, say, are
     * searching for a string in some output, may want to kill the process once
     * they have found it
     * @param output The output
     * @param closed If true, the output stream is closed and this should be the
     * final call to this method
     */
    void onOutput(ProcessControl<?, ?> process, ByteBuffer output,
            boolean closed);

    /**
     * Get an aggregated output result - note that this method may be called at
     * any time, and should not assume that the streams have already been closed
     * or the process exited normally.
     *
     * @return A result
     */
    T result();

    /**
     * Creates a new OutputHandler that emits a string.
     *
     * @return An output handler
     */
    static StringOutputHandler string()
    {
        return new StringOutputHandlerImpl();
    }

    static final OutputHandler<Void> NULL = new OutputHandler<Void>()
    {
        @Override
        public void onOutput(ProcessControl<?, ?> process, ByteBuffer bb,
                boolean bln)
        {
            // do nothing
        }

        @Override
        public Void result()
        {
            return null;
        }

    };

    default <R> OutputHandler<R> map(Function<T, R> func)
    {
        return new OutputHandler<>()
        {
            @Override
            public void onOutput(ProcessControl<?, ?> process, ByteBuffer output,
                    boolean closed)
            {
                OutputHandler.this.onOutput(process, output, closed);
            }

            @Override
            public R result()
            {
                return func.apply(OutputHandler.this.result());
            }
        };
    }
}
