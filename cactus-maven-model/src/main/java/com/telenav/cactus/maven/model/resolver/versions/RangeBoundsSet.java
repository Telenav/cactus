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

import java.util.Iterator;
import java.util.List;

/**
 * A set of bounds that express valid dependency ranges, as described
 * <a href="https://maven.apache.org/ref/3.2.1/maven-artifact/apidocs/org/apache/maven/artifact/versioning/VersionRange.html#createFromVersionSpec(java.lang.String)">here</a>.
 * These are groups of one or two versions delimited by ( or [ and ] or )
 * (parens indicate exclusive bounds, brackets indicate inclusive bounds). Each
 * bounds is a test, and to match, one of the tests must pass.
 *
 * @author Tim Boudreau
 */
final class RangeBoundsSet implements VersionPredicate<String>
{
    private final List<RangeBounds> terms;
    private final boolean valid;
    private final String original;

    RangeBoundsSet(List<RangeBounds> terms, boolean valid, String original)
    {
        this.terms = terms;
        this.valid = valid;
        this.original = original;
    }

    @Override
    public boolean isValid()
    {
        boolean result = valid;
        if (result)
        {
            for (RangeBounds rb : terms)
            {
                if (!rb.isValid())
                {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public boolean test(String encounteredVersion)
    {
        for (RangeBounds t : terms)
        {
            if (t.test(encounteredVersion))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(original).append(" -> {");
        for (Iterator<RangeBounds> it = terms.iterator(); it.hasNext();)
        {
            sb.append(it.next());
            if (it.hasNext())
            {
                sb.append(" / ");
            }
        }
        return sb.append('}').toString();
    }

}
