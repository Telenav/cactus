package com.telenav.cactus.maven.tree;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.util.preconditions.Checks;
import com.telenav.cactus.maven.tree.Problem.Severity;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toCollection;

/**
 * The set of problems found by a consistency check.
 *
 * @author Tim Boudreau
 */
public final class Problems implements Iterable<Problem>
{
    private final Set<Problem> problems;

    public Problems()
    {
        problems = ConcurrentHashMap.newKeySet();
    }

    private Problems(Set<Problem> problems)
    {
        this.problems = problems;
    }

    public Problems filter(Predicate<Problem> test)
    {
        return new Problems(problems
                .stream()
                .filter(test)
                .collect(toCollection(ConcurrentHashMap::newKeySet)));
    }

    public Problems filter(Function<Problem, Severity> transform)
    {
        Set<Problem> result = ConcurrentHashMap.newKeySet();
        for (Problem p : problems)
        {
            Severity s = transform.apply(p);
            if (s == null || s == p.severity())
            {
                result.add(p);
            }
            else
            {
                result.add(p.withSeverity(s));
            }
        }
        return new Problems(result);
    }

    public boolean hasFatal()
    {
        for (Problem p : this)
        {
            if (p.isFatal())
            {
                return true;
            }
        }
        return false;
    }

    Problems add(Problem p)
    {
        problems.add(Checks.notNull("p", p));
        return this;
    }

    Problems add(String msg, Severity sev)
    {
        return add(new SimpleProblem(msg, sev));
    }

    Problems add(String msg)
    {
        return add(new SimpleProblem(msg));
    }

    public boolean hasProblems()
    {
        return !problems.isEmpty();
    }

    public void ifProblems(ThrowingRunnable run) throws Exception
    {
        if (hasProblems())
        {
            run.run();
        }
    }

    public <T extends Problem> Set<T> find(Class<T> type)
    {
        Set<T> result = new LinkedHashSet<>();
        for (Problem p : problems)
        {
            p.as(type).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (problems.isEmpty())
        {
            sb.append("-no-problems-");
        }
        else
        {
            for (Problem p : this)
            {
                if (sb.length() > 0)
                {
                    sb.append("\n\n");
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    @Override
    public Iterator<Problem> iterator()
    {
        return Collections.unmodifiableSet(problems).iterator();
    }

    private static final class SimpleProblem extends Problem
    {
        private final String message;
        private final Severity severity;

        public SimpleProblem(String message)
        {
            this(message, Severity.FATAL);
        }

        public SimpleProblem(String message, Severity severity)
        {
            this.message = message;
            this.severity = severity;
        }

        @Override
        public Severity severity()
        {
            return severity;
        }

        @Override
        protected String computeMessage()
        {
            return message;
        }
    }
}
