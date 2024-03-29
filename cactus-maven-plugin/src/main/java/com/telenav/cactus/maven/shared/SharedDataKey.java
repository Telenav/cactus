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

import com.mastfrog.util.preconditions.Checks;
import java.util.Optional;

/**
 * A type-safe key into an instance of SharedDate, which allows mojos to share
 * data within a run.
 *
 * @author Tim Boudreau
 */
public final class SharedDataKey<T>
{

    private final Class<T> type;
    private final String name;

    SharedDataKey(Class<T> type, String name)
    {
        this.type = Checks.notNull("type", type);
        this.name = Checks.notNull("name", name);
    }

    @SuppressWarnings("unchecked")
    public static <T> SharedDataKey<T> of(String name, Class<? super T> type)
    {
        return new SharedDataKey<>((Class<T>) type, name);
    }

    @SuppressWarnings("unchecked")
    public static <T> SharedDataKey<T> of(Class<? super T> type)
    {
        // use ? super T so we can pass List for List<Foo>, etc.
        return of(type.getName(), (Class<T>) type);
    }

    public <R> Optional<T> cast(R obj)
    {
        if (obj == null)
        {
            return Optional.empty();
        }
        if (type.isInstance(obj))
        {
            return Optional.of(type.cast(obj));
        }
        return Optional.empty();
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
        {
            if (o == null || o.getClass() != SharedDataKey.class)
            {
                return false;
            }
        }
        SharedDataKey<?> k = (SharedDataKey<?>) o;
        return k.type == type && name.equals(k.name);
    }

    @Override
    public int hashCode()
    {
        return name.hashCode() + (3 * type.hashCode());
    }

    @Override
    public String toString()
    {
        return name + "(" + type.getSimpleName() + ")";
    }

}
