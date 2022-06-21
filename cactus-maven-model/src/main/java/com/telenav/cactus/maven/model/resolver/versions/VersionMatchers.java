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

import com.mastfrog.function.state.Bool;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.lang.Math.max;

/**
 * Handles Maven's (dreadful, easy to create nonsensical or matches-nothing
 * patterns) version ranges. For an enlightening discussion of why version
 * ranges are just not a great idea, see
 * <a href="http://wiki.apidesign.org/wiki/RangeDependencies">this article</a>.
 * <p>
 * Nonetheless, people use them, so here we are.
 * </p>
 *
 * @author Tim Boudreau
 */
public final class VersionMatchers
{

    private static int visitGroups(String spec, Bool valid,
            Consumer<String> groupConsumer)
    {
        int result = 0;
        valid.set(true);
        // It does not quite work to use a regular expression, because we would
        // need to match a group of [[(].*?[)]], and taht is not a valid regex.
        StringBuilder currGroup = new StringBuilder();
        boolean inGroup = false;
        int maxLength = 0;
        for (int i = 0; i < spec.length(); i++)
        {
            char c = spec.charAt(i);
            switch (c)
            {
                case '[':
                case '(':
                    if (inGroup)
                    {
                        valid.set(false);
                    }
                    else
                    {
                        inGroup = true;
                        currGroup.setLength(0);
                    }
                    currGroup.append(c);
                    break;
                case ',':
                    if (inGroup)
                    {
                        currGroup.append(c);
                    }
                    break;
                case ')':
                case ']':
                    if (inGroup)
                    {
                        currGroup.append(c);
                        inGroup = false;
                        maxLength = max(maxLength, currGroup.length());
                        groupConsumer.accept(currGroup.toString());
                        result++;
                        currGroup.setLength(0);
                    }
                    else
                    {
                        valid.set(false);
                    }
                    break;
                default:
                    if (!inGroup)
                    {
                        valid.set(false);
                    }
                    currGroup.append(c);
            }
        }
        if (maxLength == 0)
        {
            valid.set(false);
        }
        return result;
    }

    /**
     * Get an appropriate matcher for a dependency's version specification.
     *
     * @param spec The specification
     * @return A predicate which can match it
     */
    public static VersionPredicate<String> matcher(String spec)
    {
        VersionPredicate<String> result = matcherInternal(spec);
        if (!result.isValid())
        {
            System.err.println("Invalid range spec: '" + spec + "'");
            result = new ExactMatcher(spec);
        }
        return result;
    }

    static VersionPredicate<String> matcherInternal(String spec)
    {
        String stripped = stripWhitespace(notNull("spec", spec));
        if (stripped.endsWith("-SNAPSHOT"))
        {
            return new ExactMatcher(stripped);
        }
        // find all the groups delimited by brackets or parens:
        if (looksLikeRange(stripped)) // there is at least one
        {
            List<RangeBounds> terms = new ArrayList<>();
            Bool validInput = Bool.create();
            visitGroups(spec, validInput, bounds ->
            {
                terms.add(new RangeBounds(bounds));
            });
            return new RangeBoundsSet(terms, validInput.getAsBoolean(), stripped);
        }
        if (stripped.indexOf('.') < 0)
        {
            // No dotted elements to strip
            return new ExactMatcher(stripped);
        }
        return new PrefixMatcher(stripped);
    }

    public static boolean looksLikeRange(String version)
    {
        // Must have at least [] and something in between
        boolean result = version.length() > 3;
        if (result)
        {
            char first = version.charAt(0);
            char last = version.charAt(version.length() - 1);
            switch (first)
            {
                case '[':
                case '(':
                    switch (last)
                    {
                        case ']':
                        case ')':
                            break;
                        default:
                            result = false;
                    }
                    break;
                default:
                    result = false;
            }
        }
        return result;
    }

    static String stripWhitespace(String version)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < version.length(); i++)
        {
            char c = version.charAt(i);
            if (Character.isWhitespace(c))
            {
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    private VersionMatchers()
    {
        throw new AssertionError();
    }
}
