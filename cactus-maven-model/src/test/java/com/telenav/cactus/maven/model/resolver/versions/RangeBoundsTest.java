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

import org.junit.jupiter.api.Test;

import static com.telenav.cactus.maven.model.resolver.versions.RangeBounds.commaSplitWithBlanks;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 *
 * @author timb
 */
public class RangeBoundsTest
{

    @Test
    public void testCommaSplitWithBlanks()
    {
        assertSplit("1.0", "1.0");
        assertSplit("1.0,1.1", "1.0", "1.1");
        assertSplit("1.0,1.1,75", "1.0", "1.1", "75");
        assertSplit(",1.0", "", "1.0");
        assertSplit(",1.0,", "", "1.0", "");
        assertSplit(",1.0,1.1", "", "1.0", "1.1");
        assertSplit("1.0,1.1,", "1.0", "1.1", "");
        assertSplit(",1.0,,1.1", "", "1.0", "", "1.1");
        assertSplit(",1.0,,1.1,", "", "1.0", "", "1.1", "");
        assertSplit(",", "", "");
        assertSplit(",,", "", "", "");
        assertSplit(",,moo", "", "", "moo");
        assertSplit("moo,,", "moo", "", "");
    }

    private static void assertSplit(String what, String... expected)
    {
        String[] got = commaSplitWithBlanks(what);
        assertArrayEquals(expected, got, "Expected " + stringify(expected)
                + " but got " + stringify(got));
    }

    private static String stringify(String[] parts)
    {
        // The string value of [""] is "" which is indistinguishable
        // from an empty array
        StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < parts.length; i++)
        {
            sb.append('\'').append(parts[i]).append('\'');
            if (i < parts.length - 1)
            {
                sb.append(",");
            }
        }
        return sb.append('}').toString();
    }

}
