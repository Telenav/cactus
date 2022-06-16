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
import com.mastfrog.util.strings.Strings;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Just a little abstraction for build logging that lets us build a trail of
 * prefixes to make it clear what did what and what caused it to run.
 *
 * @author Tim Boudreau
 */
public class BuildLog
{

    private static final ThreadLocal<BuildLog> LOG = new ThreadLocal<>();
    private final String prefix;
    private final Logger logger;

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

    public BuildLog(Class<?> context)
    {
        this(null, LoggerFactory.getLogger(context));
    }

    public void ifDebug(Runnable run)
    {
        if (logger.isDebugEnabled())
        {
            run.run();
        }
    }

    private static void withLog(BuildLog log, ThrowingRunnable run) throws Exception
    {
        BuildLog old = LOG.get();
        try
        {
            LOG.set(log);
            run.run();
        } finally
        {
            LOG.set(old);
        }
    }

    public static BuildLog get()
    {
        BuildLog log = LOG.get();
        if (log == null)
        {
            log = new BuildLog();
        }
        return log;
    }

    public void run(ThrowingRunnable consumer) throws Exception
    {
        withLog(this, () ->
        {
            try
            {
                consumer.run();
            } catch (Exception | Error e)
            {
                logger.error(prefix == null ? "root" : prefix, e);
                throw e;
            }
        });
    }

    public BuildLog child(String name)
    {
        String pfx = prefix == null ? name : prefix + ":" + name;
        return new BuildLog(pfx, logger);
    }

    private String prefixed(String what)
    {
        return prefix == null ? what : prefix + ": " + what;
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
        } else
        {
            linesConsumer.accept(prefixed(what));
        }
    }

    public BuildLog info(String what)
    {
        if (logger.isInfoEnabled())
        {
            logSplit(what, logger::info);
        }
        return this;
    }

    public BuildLog info(String what, Throwable thrown)
    {
        logger.info(prefixed(what), thrown);
        return this;
    }

    public BuildLog info(String what, Object... args)
    {
        logger.info(prefixed(what), args);
        return this;
    }

    public BuildLog error(String what)
    {
        logSplit(what, logger::error);
        return this;
    }

    public BuildLog error(String what, Throwable thrown)
    {
        logger.error(prefixed(what), thrown);
        return this;
    }

    public BuildLog error(String what, Object... args)
    {
        logger.error(prefixed(what), args);
        return this;
    }

    public BuildLog warn(String what)
    {
        logSplit(what, logger::warn);
        return this;
    }

    public BuildLog warn(String what, Throwable thrown)
    {
        logger.warn(prefixed(what), thrown);
        return this;
    }

    public BuildLog warn(String what, Object... args)
    {
        logger.warn(prefixed(what), args);
        return this;
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
}
