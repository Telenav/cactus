package com.telenav.cactus.maven.model;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingFunction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Tim Boudreau
 */
final class MemoizingPomResolver implements PomResolver
{

    private final PomResolver delegate;
    private final Map<String, ThrowingOptional<Pom>> sansVersion = new ConcurrentHashMap<>();
    private final Map<String, ThrowingOptional<Pom>> withVersion = new ConcurrentHashMap<>();
    private final Map<Pom, PropertyResolver> resolvers = new ConcurrentHashMap<>();

    public MemoizingPomResolver(PomResolver delegate)
    {
        this.delegate = delegate;
    }
    
    @Override
    public PropertyResolver propertyResolver(Pom pom) {
        ThrowingFunction<Pom, PropertyResolver> sup = PomResolver.super::propertyResolver;
        return resolvers.computeIfAbsent(pom, sup.toNonThrowing());
    }

    @Override
    public PomResolver or(PomResolver parent)
    {
        return delegate.or(parent).memoizing();
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId)
    {
        String key = groupId + ":" + artifactId;

        return sansVersion.computeIfAbsent(key, k ->
        {
            ThrowingOptional<Pom> result = delegate.get(groupId, artifactId);
            result.ifPresent(pom ->
            {
                pom.coords.version().ifPresent(ver ->
                {
                    String vk = k + ":" + ver;
                    withVersion.put(vk, ThrowingOptional.of(pom));
                });
            });
            return result;
        });
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId, String version)
    {
        String key = groupId + ":" + artifactId + ":" + version;
        return withVersion.computeIfAbsent(key, k ->
        {
            return delegate.get(groupId, artifactId, version);
        });
    }

    @Override
    public PomResolver memoizing()
    {
        return this;
    }
    
    public String toString() {
        return "memo(" + delegate + ")";
    }
}
