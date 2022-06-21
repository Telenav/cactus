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
 * A single bound with a single term.
 *
 * @author Tim Boudreau
 */
final class Bound implements VersionPredicate<String>
{
    private final Term term;
    private final boolean isLower;
    private final boolean isUpper;
    private final boolean exclusiveLower;
    private final boolean exclusiveUpper;
    private final Set<ComparisonRelation> allowedRelations;

    Bound(Term value, boolean isLower, boolean isUpper,
            boolean exclusiveLower, boolean exclusiveUpper,
            Set<ComparisonRelation> allowedRelations)
    {
        this.term = value;
        this.isLower = isLower;
        this.isUpper = isUpper;
        this.exclusiveLower = exclusiveLower;
        this.exclusiveUpper = exclusiveUpper;
        this.allowedRelations = allowedRelations;
    }

    @Override
    public boolean isValid()
    {
        return term.isValid() && (isUpper || isLower)
                && !allowedRelations.isEmpty();
    }

    @Override
    public String toString()
    {
        // Just emits a compact representation of what this predicate
        // does using fancy unicode arrows.
        StringBuilder sb = new StringBuilder();
        if (isLower)
        {
            if (exclusiveLower)
            {
                sb.append('⤓');
            }
            else
            {
                sb.append('↓');
            }
        }
        if (isUpper)
        {
            if (exclusiveUpper)
            {
                sb.append('⤒');
            }
            else
            {
                sb.append('↑');
            }
        }
        ComparisonRelation.writeHumanReadable(allowedRelations, sb);
        sb.append(' ');
        sb.append(term);
        return sb.toString();
    }

    @Override
    public boolean test(String t)
    {
        if (t.isEmpty() || (t.equals(term.toString()) && !allowedRelations
                .contains(ComparisonRelation.EQUAL)))
        {
            return false;
        }
        ComparisonRelation rel = term.apply(t);
        boolean result = allowedRelations.contains(rel);
        if (result)
        {
            if (rel == ComparisonRelation.EQUAL)
            {
                if (isLower && exclusiveLower)
                {
                    result = false;
                }
                else
                    if (isUpper && exclusiveUpper)
                    {
                        result = false;
                    }
            }
        }
        return result;
    }

}
