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
package com.telenav.cactus.maven.log;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.util.strings.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Just a little abstraction for build logging that lets us build a trail of prefixes to make it clear what did what and
 * what caused it to run.
 *
 * @author Tim Boudreau
 */
public class BuildLog implements Consumer<String>
{
    private static final ThreadLocal<BuildLog> LOG = new ThreadLocal<>();

    public static BuildLog get()
    {
        BuildLog log = LOG.get();
        if (log == null)
        {
            log = new BuildLog();
        }
        return log;
    }

    private final String prefix;

    private final Logger logger;

    public BuildLog(Class<?> context)
    {
        this(null, LoggerFactory.getLogger(context));
    }

    public BuildLog(String prefix, String context)
    {
        this(prefix, LoggerFactory.getLogger(context));
    }

    BuildLog(String prefix, Logger logger)
    {
        this.prefix = prefix;
        this.logger = logger;
    }

    BuildLog(String prefix)
    {
        this(prefix, LoggerFactory.getLogger(BuildLog.class));
    }

    BuildLog()
    {
        this((String) null);
    }

    @Override
    public void accept(String t)
    {
        info(t);
    }

    @SuppressWarnings("unused")
    public void benchmark(String task, ThrowingRunnable run)
    {
        info("Begin " + task);
        long then = System.currentTimeMillis();
        try
        {
            run.toNonThrowing().run();
        }
        finally
        {
            info(task + " took " + (System.currentTimeMillis() - then) + " milliseconds");
        }
    }

    public <T> T benchmark(String task, ThrowingSupplier<T> run)
    {
        debug("Begin " + task);
        long then = System.currentTimeMillis();
        try
        {
            return run.asSupplier().get();
        }
        finally
        {
            debug(task + " took " + (System.currentTimeMillis() - then) + " milliseconds");
        }
    }

    public BuildLog child(String name)
    {
        String pfx = prefix == null
                ? name
                : prefix + ":" + name;
        return new BuildLog(pfx, logger);
    }

    public BuildLog debug(String what)
    {
        if (logger.isDebugEnabled())
        {
            logSplit(what, logger::debug);
        }
        return this;
    }

    public BuildLog debug(String what, Throwable thrown)
    {
        logger.debug(prefixed(what), thrown);
        return this;
    }

    public BuildLog debug(String what, Object... args)
    {
        logger.debug(prefixed(what), args);
        return this;
    }

    public BuildLog debug(Supplier<String> what)
    {
        if (logger.isDebugEnabled())
        {
            logger.debug(prefixed(what.get()));
        }
        return this;
    }

    public BuildLog error(String what)
    {
        logSplit(what, logger::error);
        println(System.err, what);
        return this;
    }

    public BuildLog error(String what, Throwable thrown)
    {
        what = prefixed(what);
        logger.error(what, thrown);
        println(System.err, thrown, what);
        return this;
    }

    public BuildLog error(String what, Object... args)
    {
        what = prefixed(what);
        logger.error(what, args);
        println(System.err, what, args);
        return this;
    }

    public void ifDebug(Runnable run)
    {
        if (logger.isDebugEnabled())
        {
            run.run();
        }
    }

    public BuildLog info(String what)
    {
        if (logger.isInfoEnabled())
        {
            logSplit(what, logger::info);
        }
        println(System.out, what);
        return this;
    }

    public BuildLog info(String what, Throwable thrown)
    {
        what = prefixed(what);
        logger.info(what, thrown);
        println(System.out, thrown, what);
        return this;
    }

    public BuildLog info(String what, Object... args)
    {
        what = prefixed(what);
        logger.info(what, args);
        println(System.out, what, args);
        return this;
    }

    public void run(ThrowingRunnable consumer) throws Exception
    {
        withLog(this, () ->
        {
            try
            {
                consumer.run();
            }
            catch (Exception | Error e)
            {
                if (Boolean.getBoolean("cactus.debug"))
                {
                    logger.error(prefix == null
                            ? "root"
                            : prefix, e);
                }
                else
                {
                    logger.error(e.getMessage());
                }
                throw e;
            }
        });
    }

    public BuildLog warn(String what)
    {
        logSplit(what, logger::warn);
        println(System.out, what);
        return this;
    }

    public BuildLog warn(String what, Throwable thrown)
    {
        what = prefixed(what);
        logger.warn(what, thrown);
        println(System.out, thrown, what);
        return this;
    }

    public BuildLog warn(String what, Object... args)
    {
        what = prefixed(what);
        logger.warn(what, args);
        println(System.out, what, args);
        return this;
    }

    private static void withLog(BuildLog log, ThrowingRunnable run) throws Exception
    {
        BuildLog old = LOG.get();
        try
        {
            LOG.set(log);
            run.run();
        }
        finally
        {
            LOG.set(old);
        }
    }

    private void logSplit(String what, Consumer<String> linesConsumer)
    {
        if (what.indexOf('\n') >= 0)
        {
            Strings.split('\n', what, seq ->
            {
                linesConsumer.accept(prefixed(seq.toString()));
                return true;
            });
        }
        else
        {
            linesConsumer.accept(prefixed(what));
        }
    }

    private void println(PrintStream out, String message, Object... arguments)
    {
        String formattedMessage = MessageFormatter.basicArrayFormat(message, arguments);
        out.println(formattedMessage);
        out.flush();
    }

    private void println(PrintStream out, Throwable throwable, String message, Object... arguments)
    {
        println(out, message, arguments);
        if (throwable != null)
        {
            throwable.printStackTrace(out);
        }
    }

    private String prefixed(String what)
    {
        return prefix == null
                ? what
                : prefix + ": " + what;
    }
}
