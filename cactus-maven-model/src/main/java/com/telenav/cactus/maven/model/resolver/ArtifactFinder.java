package com.telenav.cactus.maven.model.resolver;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.maven.model.Dependency;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface that a caller can provide, to be used within some context, to force
 * loading of a resource that cannot be found during dependency computation -
 * using Maven, or whatever mechanism.
 *
 * @author Tim Boudreau
 */
@FunctionalInterface
public interface ArtifactFinder
{
    Optional<Path> find(String groupId, String artifactId, String version, String type);
    
    default void run(Runnable toRun) {
        LocalRepoResolver.INSTANCE.withArtifactFinder(this, toRun);
    }
}
