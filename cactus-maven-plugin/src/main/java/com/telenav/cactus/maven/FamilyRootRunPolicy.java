package com.telenav.cactus.maven;

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.trigger.RunPolicy;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * A RunPolicy for the lexakai mojo that runs against only children of the root.
 *
 * @author Tim Boudreau
 */
public class FamilyRootRunPolicy implements RunPolicy
{
    @Override
    public boolean shouldRun(MavenProject invokedOn, MavenSession session)
    {
        if (!"pom".equals(invokedOn.getPackaging()))
        {
            return false;
        }
        if (invokedOn.getModules().isEmpty())
        {
            return false;
        }
        return GitCheckout.repository(invokedOn.getBasedir())
                .map(co ->
                {
                    return !co.isSubmoduleRoot() && !co.name().contains("/");
                }).orElse(false);
    }

}
