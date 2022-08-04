package com.telenav.cactus.maven.tree;

import java.util.Optional;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.tree.Problem.Severity.FATAL;

/**
 *
 * @author Tim Boudreau
 */
public abstract class Problem
{
    private String msg;

    public final String message()
    {
        if (msg != null)
        {
            return msg;
        }
        return msg = computeMessage();
    }

    protected abstract String computeMessage();

    @Override
    public final String toString()
    {
        return severity() + ": " + message();
    }

    public <T> Optional<T> as(Class<T> type)
    {
        if (getClass().isInstance(this))
        {
            return Optional.of(type.cast(this));
        }
        return Optional.empty();
    }

    public Severity severity()
    {
        return Severity.FATAL;
    }

    public final boolean isFatal()
    {
        return severity() == FATAL;
    }

    @Override
    public final boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (!(o instanceof Problem))
            {
                return false;
            }
        Problem p = (Problem) o;
        return p.severity() == severity() && p.message().equals(message());
    }

    @Override
    public final int hashCode()
    {
        return (severity().ordinal() + 1) * 41 * message().hashCode();
    }

    public Problem withSeverity(Severity sev)
    {
        if (sev == severity())
        {
            return this;
        }
        return new Wrapper(this, sev);
    }

    public enum Severity
    {
        NOTE,
        WARNING,
        FATAL,;

        @Override
        public String toString()
        {
            return name().toLowerCase();
        }
    }

    private static final class Wrapper extends Problem
    {
        private final Problem delegate;
        private final Severity severity;

        public Wrapper(Problem delegate, Severity severity)
        {
            this.delegate = notNull("delegate", delegate);
            this.severity = severity;
        }

        @Override
        public Problem withSeverity(Severity sev)
        {
            if (sev == severity())
            {
                return this;
            }
            else
                if (sev == delegate.severity())
                {
                    return delegate;
                }
            return delegate.withSeverity(sev);
        }

        public Severity severity()
        {
            return severity;
        }

        @Override
        public <T> Optional<T> as(Class<T> type)
        {
            return delegate.as(type).or(() -> super.as(type));
        }

        @Override
        protected String computeMessage()
        {
            return delegate.message();
        }
    }
}
