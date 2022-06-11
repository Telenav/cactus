package com.telenav.cactus.maven;

import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public interface RunPolicy
{

    boolean shouldRun(MavenProject invokedOn, MavenSession session);

    default RunPolicy and(RunPolicy other)
    {
        return (prj, sess) ->
        {
            return other.shouldRun(prj, sess) && shouldRun(prj, sess);
        };
    }

    default RunPolicy negate()
    {
        return (prj, sess) -> !shouldRun(prj, sess);
    }

    static RunPolicy forPackaging(String packaging)
    {
        return (prj, ignored) ->
        {
            return Objects.equals(packaging, prj.getPackaging());
        };
    }
}
