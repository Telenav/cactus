package com.telenav.cactus.maven.trigger;

import com.telenav.cactus.git.GitCheckout;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * A RunPolicy for the lexakai mojo that runs against only children of the root.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class FamilyRootRunPolicy implements RunPolicy
{
    static Map<Path, Boolean> CACHE = new ConcurrentHashMap<>();

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
        return CACHE.computeIfAbsent(invokedOn.getBasedir().toPath(),
                p -> _shouldRun(p, session));
    }

    private boolean _shouldRun(Path basedir, MavenSession session)
    {
        return GitCheckout.repository(basedir)
                .map(co ->
                {
                    return !co.isSubmoduleRoot() && !co.name().contains("/");
                }).orElse(false);
    }
}
