////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2011-2022 Telenav, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package com.telenav.cactus.maven.model.resolver;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.property.PropertyResolver;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Memoizing wrapper for PomResolver.
 *
 * @author Tim Boudreau
 */
final class MemoizingPomResolver implements PomResolver
{

    private final PomResolver delegate;
    private final Map<String, ThrowingOptional<Pom>> sansVersion = new ConcurrentHashMap<>();
    private final Map<String, ThrowingOptional<Pom>> withVersion = new ConcurrentHashMap<>();
    private final Map<Pom, PropertyResolver> resolvers = new ConcurrentHashMap<>();

    MemoizingPomResolver(PomResolver delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public PropertyResolver propertyResolver(Pom pom)
    {
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
                pom.coords.resolvedVersion().ifPresent(ver ->
                {
                    String vk = k + ":" + ver;
                    withVersion.put(vk, ThrowingOptional.of(pom));
                });
            });
            return result;
        });
    }

    @Override
    public ThrowingOptional<Pom> get(String groupId, String artifactId,
            String version)
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

    @Override
    public String toString()
    {
        return "memo(" + delegate + ")";
    }
}
