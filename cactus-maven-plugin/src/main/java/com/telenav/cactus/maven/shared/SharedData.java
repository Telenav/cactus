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
package com.telenav.cactus.maven.shared;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.inject.Singleton;
import org.apache.maven.plugin.MojoFailureException;

/**
 * Allows mojos to share data in a type-safe manner, for cases where some
 * pre-work (such as creating a branch) needs to be hung off the validate phase,
 * before any build has run, while post-work (such as merging a branch back to
 * the stable branch) needs to be hung off the install phase, and so cannot be
 * done by the same mojo. Uses simple typed keys into a map.
 *
 * @author Tim Boudreau
 */
@Singleton
public final class SharedData
{

    private final Map<SharedDataKey<?>, Object> contents = new ConcurrentHashMap<>();
    
    public boolean has(SharedDataKey<?> key) {
        return contents.containsKey(key);
    }
    
    public void ensureHas(SharedDataKey<?> key, String message) throws MojoFailureException {
        if (!has(key)) {
            String msg = message + " Missing: " + key;
            throw new MojoFailureException(this, msg, msg);
        }
    }

    public <T> SharedData put(SharedDataKey<T> key, T obj)
    {
        contents.put(key, obj);
        return this;
    }

    public <T> Optional<T> get(SharedDataKey<T> key)
    {
        return key.cast(contents.get(key));
    }

    public <T> Optional<T> remove(SharedDataKey<T> key)
    {
        return key.cast(contents.remove(key));
    }

    public <T> T computeIfAbsent(SharedDataKey<T> key, Supplier<T> supp)
    {
        Optional<T> result = get(key);
        if (!result.isPresent())
        {
            T obj = supp.get();
            if (obj == null)
            {
                throw new IllegalArgumentException("Supplier returned null: "
                        + supp);
            }
            put(key, obj);
            return obj;
        }
        return result.get();
    }
}
