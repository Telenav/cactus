package com.telenav.cactus.maven;

import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingRunnable;
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

    BuildLog(Class<?> context)
    {
        this(null, LoggerFactory.getLogger(context));
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

    public BuildLog info(String what)
    {
        logger.info(prefixed(what));
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
        logger.error(prefixed(what));
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
        logger.warn(prefixed(what));
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
}
