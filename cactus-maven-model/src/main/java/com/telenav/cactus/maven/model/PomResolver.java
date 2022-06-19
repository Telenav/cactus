package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;

/**
 *
 * @author Tim Boudreau
 */
public interface PomResolver
{

    static PomResolver local()
    {
        return LocalRepoResolver.INSTANCE;
    }
    
    default PropertyResolver propertyResolver(Pom pom) throws Exception {
        return new ParentsPropertyResolver(pom, this);
    }

    default PomResolver memoizing()
    {
        return new MemoizingPomResolver(this);
    }
    
    default <T extends MavenIdentified & MavenVersioned> ThrowingOptional<Pom> get(T obj) {
        ThrowingOptional<String> ver = obj.version();
        if (ver.isPresent()) {
            return get(obj.groupId(), obj.artifactId(), ver.get());
        } else {
            return get(obj.groupId(), obj.artifactId());
        }
    }

    default ThrowingOptional<Pom> get(String groupId, String artifactId, String version)
    {
        return get(groupId, artifactId).flatMapThrowing(pom
                -> version.equals(pom.coords.version)
                   ? ThrowingOptional.of(pom)
                   : ThrowingOptional.empty());
    }

    public ThrowingOptional<Pom> get(String groupId, String artifactId);

    default PomResolver or(PomResolver parent)
    {
        return new PomResolver()
        {
            @Override
            public ThrowingOptional<Pom> get(String groupId, String artifactId)
            {
                return PomResolver.this.get(groupId, artifactId)
                        .or(parent.get(groupId, artifactId));
            }

            @Override
            public ThrowingOptional<Pom> get(String groupId, String artifactId,
                    String version)
            {
                return PomResolver.this.get(groupId, artifactId, version)
                        .or(parent.get(groupId, artifactId, version));
            }

            @Override
            public String toString()
            {
                return PomResolver.this.toString() + " -> " + parent;
            }
        };
    }
}
