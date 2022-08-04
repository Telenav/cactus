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

/**
 * A single occurrence of a property in a single pom, representing a version of
 * a family or a project. Will be parameterized on MavenCoordinates or
 * ProjectFamily depending on what it applies to.
 *
 * @author Tim Boudreau
 */
final class VersionProperty<T>
{
    private final String property;
    private final Pom in;
    private final T target;
    private final String oldValue;

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

    public Pom pom()
    {
        return in;
    }

    public String oldValue()
    {
        return oldValue;
    }

    public String property()
    {
        return property;
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
        return in.path().getParent().getFileName().resolve(in.path()
                .getFileName())
                + "\t" + property + " in " + target
                + " currently " + oldValue;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else
            if (obj == null || VersionProperty.class != obj.getClass())
            {
                return false;
            }
        VersionProperty<?> vp = (VersionProperty<?>) obj;
        return property.equals(vp.property) && in.equals(vp.in) && target
                .equals(vp.target);
    }

    public int hashCode()
    {
        return ((71 * property.hashCode()) + (3 * target.hashCode()))
                * in.hashCode();
    }
}
