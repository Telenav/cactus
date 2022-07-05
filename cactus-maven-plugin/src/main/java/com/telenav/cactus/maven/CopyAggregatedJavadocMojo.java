package com.telenav.cactus.maven;

import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Variation of the copy-javadoc mojo which only copies aggregated javadoc for pom projects.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.POST_SITE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.PER_LOOKUP,
        name = "copy-aggregated-javadoc", threadSafe = true)

public class CopyAggregatedJavadocMojo extends CopyJavadocMojo
{
    public CopyAggregatedJavadocMojo()
    {
        super(new FamilyRootRunPolicy());
    }
}
