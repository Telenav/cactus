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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A single bracket or paren delimited version group, which may have one or two
 * elements.
 *
 * @author Tim Boudreau
 */
final class RangeBounds implements VersionPredicate<String>
{
    private final List<VersionPredicate<String>> constraints = new ArrayList<>();
    private boolean valid = true;
    private int targetMatchCount = 1;

    RangeBounds(String spec)
    {
        // We are receiving a bracketed term, which may be one value
        // or two, e.g. (,1.0]
        Set<ComparisonRelation> allowedRelationsLower = EnumSet.noneOf(
                ComparisonRelation.class);
        Set<ComparisonRelation> allowedRelationsUpper = EnumSet.noneOf(
                ComparisonRelation.class);
        String subspec = spec.substring(1, spec.length() - 1);
        String[] parts = commaSplitWithBlanks(subspec);
        boolean exclusiveLower = spec.charAt(0) == '(';
        boolean exclusiveUpper = spec.charAt(spec.length() - 1) == ')';
        if (!exclusiveLower)
        {
            allowedRelationsLower.add(ComparisonRelation.EQUAL);
        }
        if (!exclusiveUpper)
        {
            allowedRelationsUpper.add(ComparisonRelation.EQUAL);
        }
        List<String> stringValues = new ArrayList<>();
        if (parts.length == 1)
        {
            if (!parts[0].isEmpty())
            {
                allowedRelationsLower.add(ComparisonRelation.EQUAL);
                allowedRelationsUpper.add(ComparisonRelation.EQUAL);
                stringValues.add(parts[0]);
            }
        }
        else
        {
            if (parts[0].isEmpty())
            {
                // leading comma - range is extended below
                allowedRelationsLower.add(ComparisonRelation.BELOW);
            }
            if (parts[parts.length - 1].isEmpty())
            {
                // trailing comma - range is extended above
                allowedRelationsUpper.add(ComparisonRelation.ABOVE);
            }
            // Now find all the terms:
            for (String part : parts)
            {
                if (!part.isEmpty())
                {
                    stringValues.add(part);
                }
            }
        }

        switch (stringValues.size())
        {
            case 0:
                valid = false;
                break;
            case 1:
                if (allowedRelationsUpper.isEmpty())
                {
                    allowedRelationsUpper.add(ComparisonRelation.BELOW);
                }
                if (allowedRelationsLower.isEmpty())
                {
                    allowedRelationsLower.add(ComparisonRelation.ABOVE);
                }
                constraints.add(new Bound(new Term(stringValues.get(0)), true,
                        false, exclusiveLower, exclusiveUpper,
                        allowedRelationsLower));
                constraints.add(new Bound(new Term(stringValues.get(0)), false,
                        true, exclusiveLower, exclusiveUpper,
                        allowedRelationsUpper));
                valid = true;
                break;
            default:
                allowedRelationsLower.add(ComparisonRelation.ABOVE);
                allowedRelationsUpper.add(ComparisonRelation.BELOW);
                if (!allowedRelationsLower.contains(ComparisonRelation.BELOW))
                {
                    targetMatchCount = 2;
                }
                if (!allowedRelationsUpper.contains(ComparisonRelation.ABOVE))
                {
                    targetMatchCount = 2;
                }
                constraints.add(new Bound(new Term(stringValues.get(0)), true,
                        false, exclusiveLower, exclusiveUpper,
                        allowedRelationsLower));
                constraints.add(new Bound(new Term(stringValues.get(stringValues
                        .size() - 1)),
                        false, true, exclusiveLower, exclusiveUpper,
                        allowedRelationsUpper));
                valid &= stringValues.size() == 2;
        }
        if (parts.length >= 3)
        {
            valid = false;
        }
    }

    static String[] commaSplitWithBlanks(String s)
    {
        List<String> result = new ArrayList<>();
        StringBuilder curr = new StringBuilder();
        for (int i = 0; i < s.length(); i++)
        {
            char c = s.charAt(i);
            switch (c)
            {
                case ',':
                    result.add(curr.toString());
                    curr.setLength(0);
                    if (i == s.length() - 1)
                    {
                        result.add("");
                    }
                    break;
                default:
                    curr.append(c);
                    if (i == s.length() - 1)
                    {
                        result.add(curr.toString());
                    }
                    break;
            }
        }
        return result.toArray(String[]::new);
    }

    @Override
    public boolean isValid()
    {
        boolean result = valid;
        if (result)
        {
            for (VersionPredicate<String> b : constraints)
            {
                if (!b.isValid())
                {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        if (!valid)
        {
            sb.append('!');
        }
        for (Iterator<VersionPredicate<String>> it = constraints.iterator(); it
                .hasNext();)
        {
            sb.append(it.next());
            if (it.hasNext())
            {
                sb.append(" / ");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean test(String encounteredVersion)
    {
        int count = 0;
        for (Predicate<String> tc : constraints)
        {
            boolean success = tc.test(encounteredVersion);
            if (success)
            {
                count++;
            }
        }
        return count == targetMatchCount;
    }

}
