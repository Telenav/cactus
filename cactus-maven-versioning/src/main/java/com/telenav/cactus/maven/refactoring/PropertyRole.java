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

import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.PomVersion;

/**
 * Roles we recognize for properties.
 *
 * @author timb
 */
enum PropertyRole
{
    FAMILY_VERSION,
    FAMILY_PREV_VERSION,
    PROJECT_VERSION,
    PROJECT_PREV_VERSION,
    OTHER;

    @Override
    public String toString()
    {
        return name().replace('_', '-').toLowerCase();
    }

    boolean isFamily()
    {
        return this == FAMILY_PREV_VERSION || this == FAMILY_VERSION;
    }

    boolean isProject()
    {
        return this == PROJECT_PREV_VERSION || this == PROJECT_VERSION;
    }

    boolean isPrevious()
    {
        return this == PROJECT_PREV_VERSION || this == FAMILY_PREV_VERSION;
    }

    PomVersion value(VersionChange change)
    {
        if (this == OTHER)
        {
            throw new IllegalStateException("Not a version property");
        }
        if (isPrevious())
        {
            return change.oldVersion();
        }
        else
        {
            return change.newVersion();
        }
    }

}
