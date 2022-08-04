package com.telenav.cactus.maven.trigger;

import com.telenav.cactus.maven.mojobase.BaseMojo;
import org.apache.maven.project.MavenProject;

public class OrRunPolicy implements RunPolicy
{
    private final RunPolicy i;

    // Public because of classloader issues with maven instantiating
    // modularized mojos
    private final RunPolicy they;

    public OrRunPolicy(RunPolicy i, RunPolicy other)
    {
        this.i = i;
        this.they = other;
    }

    @Override
    public boolean shouldRun(BaseMojo mojo, MavenProject prj)
    {
        return i.shouldRun(mojo, prj)
                || they.shouldRun(mojo, prj);
    }

    @Override
    public String toString()
    {
        return "(" + i + " || " + they + ")";
    }
}
