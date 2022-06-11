package com.telenav.cactus.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public enum RunPolicies implements RunPolicy
{

    FIRST,
    EVERY,
    POM_PROJECT_ONLY,
    NON_POM_PROJECT_ONLY,
    LAST;

    public boolean shouldRun(MavenProject invokedOn, MavenSession session)
    {
        switch (this)
        {
            case FIRST:
                return isFirst(invokedOn, session);
            case POM_PROJECT_ONLY:
                return "pom".equals(invokedOn.getPackaging());
            case NON_POM_PROJECT_ONLY:
                return !"pom".equals(invokedOn.getPackaging());
            case EVERY:
                return true;
            case LAST:
                return isLast(invokedOn, session);
            default:
                throw new AssertionError(this);

        }
    }

    private static boolean isFirst(MavenProject invokedOn, MavenSession session)
    {
        return session.getProjects().indexOf(invokedOn) == 0;
    }

    private static boolean isLast(MavenProject invokedOn, MavenSession session)
    {
        return session.getExecutionRootDirectory().equalsIgnoreCase(invokedOn.getBasedir().toString());
    }
}
