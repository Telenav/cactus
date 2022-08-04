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
package com.telenav.cactus.maven.model.resolver.versions;

import java.util.Set;

/**
 * The output of a comparison.
 *
 * @author Tim Boudreau
 */
enum ComparisonRelation
{
    BELOW,
    EQUAL,
    ABOVE;

    static void writeHumanReadable(Set<ComparisonRelation> relations,
            StringBuilder into)
    {
        // Generates something human readable without having to
        // order the constants nonsensically
        if (relations.isEmpty())
        {
            into.append("?");
            return;
        }
        if (relations.contains(BELOW))
        {
            into.append(BELOW);
        }
        if (relations.contains(ABOVE))
        {
            into.append(ABOVE);
        }
        if (relations.contains(EQUAL))
        {
            into.append(EQUAL);
        }
    }

    @Override
    public String toString()
    {
        switch (this)
        {
            case BELOW:
                return "<";
            case ABOVE:
                return ">";
            case EQUAL:
                return "=";
            default:
                throw new AssertionError(this);
        }
    }

    static ComparisonRelation of(int comparisonValue)
    {
        switch (comparisonValue)
        {
            case 0:
                return EQUAL;
            default:
                if (comparisonValue > 0)
                {
                    return ABOVE;
                }
                else
                {
                    return BELOW;
                }
        }
    }

}
