package com.telenav.cactus.maven.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public enum DependencyScope
{

    Compile,
    Test,
    Provided,
    Runtime,
    Import;
    private Set<DependencyScope> trans;
    private Set<DependencyScope> asSet;

    @Override
    public String toString()
    {
        return name().toLowerCase();
    }

    public Set<DependencyScope> asSet()
    {
        if (asSet == null)
        {
            asSet = EnumSet.of(this);
        }
        return asSet;
    }

    @SuppressWarnings("ManualArrayToCollectionCopy")
    public static Set<DependencyScope> set(DependencyScope... scopes)
    {
        Set<DependencyScope> result = EnumSet.noneOf(DependencyScope.class);
        for (DependencyScope sc : scopes)
        {
            result.add(sc);
        }
        return result;
    }

    public Set<DependencyScope> transitivity()
    {
        if (trans != null)
        {
            return trans;
        }
        switch (this)
        {
            case Compile:
                return trans = Collections
                        .unmodifiableSet(set(Compile, Runtime, Provided));
            case Test:
                return trans = Collections.unmodifiableSet(set(Compile, Runtime,
                        Provided));
            case Provided:
                return trans = Collections.unmodifiableSet(set(Compile, Runtime,
                        Provided));
            case Import:
                return trans = Collections.emptySet();
            case Runtime:
                return trans = Collections
                        .unmodifiableSet(set(Compile, Runtime));
            default:
                throw new AssertionError(this);
        }
    }

    public static DependencyScope of(String what)
    {
        if (what == null || what.isBlank())
        {
            return null;
        }
        switch (what)
        {
            case "compile":
                return Compile;
            case "test":
                return Test;
            case "provided":
                return Provided;
            case "runtime":
                return Runtime;
            case "import":
                return Import;
            default:
                return Compile;
        }
    }

    public DependencyScope coalesce(DependencyScope other)
    {
        if (other.ordinal() < ordinal())
        {
            return other;
        }
        else
        {
            return this;
        }
    }

    public boolean includes(Dependency dep)
    {
        return transitivity().contains(dep.scope);
    }
}
