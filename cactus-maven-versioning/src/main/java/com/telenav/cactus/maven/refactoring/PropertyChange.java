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
package com.telenav.cactus.maven.refactoring;

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.ResolvablePomElement;
import java.util.Optional;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * A single property change to be made in a single pom file.
 *
 * @author Tim Boudreau
 */
final class PropertyChange<T, E extends ResolvablePomElement<E>>
{
    private final VersionProperty<T> property;
    private final E newValue;

    private PropertyChange(VersionProperty<T> property,
            E newValue)
    {
        this.property = property;
        this.newValue = newValue;
    }

    static <T, E extends ResolvablePomElement<E>>
            Optional<PropertyChange<T, E>> propertyChange(
                    VersionProperty<T> property,
                    E replacement)
    {
        if (notNull("replacement", replacement).text()
                .equals(notNull("property", property).oldValue()))
        {
            return Optional.empty();
        }
        return Optional.of(new PropertyChange<>(property, replacement));
    }

    public E newValue()
    {
        return newValue;
    }

    public String propertyName()
    {
        return property.property();
    }

    public String oldValue()
    {
        return property.oldValue();
    }

    public Pom foo()
    {
        return property.pom();
    }

    @Override
    public String toString()
    {
        return property + " -> " + newValue;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || PropertyChange.class != o.getClass())
            {
                return false;
            }
        PropertyChange pc = (PropertyChange) o;
        return pc.property.equals(property) && pc.newValue.equals(newValue);
    }

    @Override
    public int hashCode()
    {
        return (newValue.hashCode() + 1) * (71 * property.hashCode());
    }
}
