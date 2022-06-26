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
package com.telenav.cactus.util;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EnumMatcherTest
{
    enum SomeEnum
    {
        SOMETHING_WITH_HYPHENS,
        SOMETHING,
        OTHER_THING,
        Lower_Case_Thing,
    }
    static final EnumMatcher<SomeEnum> EM = EnumMatcher.enumMatcher(
            SomeEnum.class);

    @Test
    public void testMatchingSimpleNames()
    {
        for (SomeEnum en : EM)
        {
            Optional<SomeEnum> opt = EM.match(en.name());
            assertTrue(opt.isPresent(), "Got nothing for original enum name");
            assertSame(en, opt.get(), "Wrong constant for " + en);
        }
    }

    @Test
    public void testMatchingLcNames()
    {
        for (SomeEnum en : EM)
        {
            String n = en.name().toLowerCase();
            Optional<SomeEnum> opt = EM.match(en.name().toLowerCase());

            assertTrue(opt.isPresent(), "Got nothing for lc name '" + n + "'");
            assertSame(en, opt.get(), "Wrong constant for " + en + " with " + n);
        }
    }

    @Test
    public void testMatchingLcHyphenNames()
    {
        for (SomeEnum en : EM)
        {
            String n = en.name().toLowerCase().replace('_', '-');
            Optional<SomeEnum> opt = EM.match(n);
            assertTrue(opt.isPresent(),
                    "Got nothing for lc-hyhen name '" + n + "'");
            assertSame(en, opt.get(), "Wrong constant for " + en + " with " + n);
        }
    }

    @Test
    public void testIterator()
    {
        Set<SomeEnum> found = EnumSet.noneOf(SomeEnum.class);
        for (SomeEnum en : EM)
        {
            found.add(en);
        }
        assertEquals(EnumSet.allOf(SomeEnum.class), found);
    }

}
