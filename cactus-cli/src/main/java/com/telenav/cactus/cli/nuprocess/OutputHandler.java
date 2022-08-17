package com.telenav.cactus.cli.nuprocess;

import java.nio.ByteBuffer;
import java.util.function.Function;

/**
 * Callback which gets passed input (either stdin or stderr), and can collect it
 * in some fashion and turn it into a result object.
 *
 * @author Tim Boudreau
 */
public interface OutputHandler<T>
{
    /**
     * Called repeatedly when output is available for reading; the passed byte
     * buffer's position must be advanced by the amount read within the closure
     * of this method.  The output buffer may be resed, and references to it
     * should not be retained.  This method must be thread-safe with respect
     * to calls to the <code>result()</code> method.
     * 
     * @param output The output
     * @param closed If true, the output stream is closed and this should be the
     * final call to this method
     */
    void onOutput(ByteBuffer output, boolean closed);

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
    static OutputHandler<String> string()
    {
        return new StringOutputHandler();
    }

    static final OutputHandler<Void> NULL = new OutputHandler<Void>()
    {
        @Override
        public void onOutput(ByteBuffer bb, boolean bln)
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
            public void onOutput(ByteBuffer output, boolean closed)
            {
                OutputHandler.this.onOutput(output, closed);
            }

            @Override
            public R result()
            {
                return func.apply(OutputHandler.this.result());
            }
        };
    }
}
