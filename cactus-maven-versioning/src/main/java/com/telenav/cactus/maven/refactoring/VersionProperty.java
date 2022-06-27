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
import java.util.Objects;

/**
 * A property which represents a single occurrence of a property representing a
 * version. Will be parameterized on MavenCoordinates or ProjectFamily depending
 * on what it applies to.
 *
 * @author Tim Boudreau
 */
final class VersionProperty<T>
{
    final String property;
    final Pom in;
    final T target;
    final String oldValue;

    /**
     * Create a new VersionProperty.
     *
     * @param property The name of the property
     * @param in The pom it occurs in
     * @param target The target - either a PomFamily or a MavenCoordinates,
     * depending on whether the property names the version of an entire family
     * of projects or a single individual project
     * @param oldValue The value of the pom at the time it was encountered
     */
    VersionProperty(String property, Pom in, T target, String oldValue)
    {
        this.property = property;
        this.in = in;
        this.target = target;
        this.oldValue = oldValue;
    }

    public T pointsTo()
    {
        return target;
    }

    public boolean matches(Pom pom)
    {
        return pom.equals(in);
    }

    @Override
    public String toString()
    {
        return in.pom.getParent().getFileName().resolve(in.pom.getFileName())
                + "\t" + property + " in " + target + " for "
                + " currently " + oldValue;
    }

    @Override
    public int hashCode()
    {
        int hash = 7;
        hash = 97 * hash + Objects.hashCode(this.property);
        hash = 97 * hash + Objects.hashCode(this.in);
        hash = 97 * hash + Objects.hashCode(this.target);
        hash = 97 * hash + Objects.hashCode(this.oldValue);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        final VersionProperty<?> other = (VersionProperty<?>) obj;
        if (!Objects.equals(this.property, other.property))
            return false;
        if (!Objects.equals(this.oldValue, other.oldValue))
            return false;
        if (!Objects.equals(this.in, other.in))
            return false;
        return Objects.equals(this.target, other.target);
    }

}
