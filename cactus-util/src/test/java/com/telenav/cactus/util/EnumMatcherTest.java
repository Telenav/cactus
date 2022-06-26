package com.telenav.cactus.util;

import java.util.EnumSet;
import java.util.Iterator;
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
