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

import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author timb
 */
public class VersionMatchersTest
{

    @Test
    public void testGapBound()
    {
        VersionPredicate<String> vr = VersionMatchers
                .matcherInternal("(,1.0],[1.2,)");
        assertAccepts(vr, "0.9");
        assertAccepts(vr, "0.0");
        assertAccepts(vr, "0");

        assertRejects(vr, "1.0.1");
        assertRejects(vr, "1.1");
        assertRejects(vr, "1.1.1");
        assertAccepts(vr, "1.2");
        assertAccepts(vr, "1.2.1");
        assertAccepts(vr, "2.0");
        assertValid(vr);
    }

    @Test
    public void testInvalidPatterns()
    {
        assertInvalid(VersionMatchers.matcherInternal("(,1.0],x,[1.2,)"));
        assertInvalid(VersionMatchers.matcherInternal("(,1.0],[1.2,2.3,3.7)"));
        assertInvalid(VersionMatchers.matcherInternal("(,1.0],[1.2,,3.7)"));
        assertInvalid(VersionMatchers.matcherInternal("(,,)"));

        assertInvalid(VersionMatchers.matcherInternal("[1.0,7.0, 2.0)"));
        assertInvalid(VersionMatchers.matcherInternal("[1.0,,, 2.0)"));
        assertInvalid(VersionMatchers.matcherInternal("[x[1.0,,, 2.0)"));
        assertInvalid(VersionMatchers.matcherInternal("[[1.0,2.0)"));
        assertInvalid(VersionMatchers.matcherInternal("[1.0,2.0))"));
        assertInvalid(VersionMatchers.matcherInternal("[1.0,2.0)]"));
    }

    @Test
    public void testExactMatcher()
    {
        assertTrue(VersionMatchers.matcherInternal("1") instanceof ExactMatcher);
        assertTrue(
                VersionMatchers.matcherInternal("111-111-111") instanceof ExactMatcher);
        assertTrue(
                VersionMatchers.matcherInternal("42.23.7-SNAPSHOT") instanceof ExactMatcher);
    }

    @Test
    public void testRangeExclusiveUpperBound()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal(
                "[1.0,2.0)");
        assertAccepts(vr, "1.0");
        assertAccepts(vr, "1.0.1");
        assertAccepts(vr, "1.5");
        assertRejects(vr, "2.0");
        assertRejects(vr, "2.1");
        assertRejects(vr, "0.9");
        assertValid(vr);
    }

    @Test
    public void testMatchesNothing()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal("[1.0]");
        assertValid(vr);
        assertRejects(vr, "0.9");
        assertRejects(vr, "1.1");
        assertRejects(vr, "1.0");
    }

    @Test
    public void testOddlyExclusive()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal(
                "[1.0,2.0)");
        assertAccepts(vr, "1.1");
        assertAccepts(vr, "1.1.3");
        assertAccepts(vr, "1.9.9");
        assertAccepts(vr, "1.2.blah");
        assertAccepts(vr, "1.0");
        assertAccepts(vr, "1.0.1");
        assertAccepts(vr, "1.0.0");
        assertRejects(vr, "2.0");
        assertRejects(vr, "0.9");
        assertValid(vr);
    }

    @Test
    public void testRangeInclusiveUpperBound()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal(
                "[1.0,2.0]");
        assertValid(vr);
        assertAccepts(vr, "1.0");
        assertAccepts(vr, "1.0.1");
        assertAccepts(vr, "1.5");
        assertAccepts(vr, "2.0");
        assertRejects(vr, "2.1");

    }

    @Test
    public void testRangeExclusiveBothBounds()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal(
                "(1.0,2.0)");
        assertRejects(vr, "1.0");
        assertAccepts(vr, "1.0.1");
        assertAccepts(vr, "1.5");
        assertAccepts(vr, "1.9");
        assertRejects(vr, "2.0");
        assertRejects(vr, "2.1");
        assertValid(vr);
    }

    @Test
    public void testExtendedUpperBound()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal("[1.0,)");
        assertAccepts(vr, "1.0");
        assertAccepts(vr, "1.5");
        assertAccepts(vr, "2.3");
        assertRejects(vr, "0.9");
        assertValid(vr);
    }

    @Test
    public void testExtendedLowerBound()
    {
        VersionPredicate<String> vr = VersionMatchers.matcherInternal("(,1.0]");
        assertRejects(vr, "1.5");
        assertRejects(vr, "2.3");
        assertAccepts(vr, "0.9");
        assertAccepts(vr, "0.1");
        assertAccepts(vr, "0.1.1");
        assertAccepts(vr, "0.1.1");
        assertAccepts(vr, "1.0");
        assertValid(vr);
    }

    static void assertValid(VersionPredicate<?> p)
    {
        assertTrue(p.isValid(), "Should be valid: " + p + " (" + p.getClass()
                .getSimpleName() + ")");
    }

    static void assertInvalid(VersionPredicate<?> p)
    {
        assertFalse(p.isValid(), "Should be invalid: " + p + " (" + p.getClass()
                .getSimpleName() + ")");
    }

    static void assertAccepts(Predicate<String> vr, String version)
    {
        assertTrue(vr.test(version), "Did not match " + version + " with "
                + vr + " (" + vr.getClass().getSimpleName() + ")");
    }

    static void assertRejects(Predicate<String> vr, String version)
    {
        assertFalse(vr.test(version),
                "Should not have matched " + version + " with " + vr
                + " (" + vr.getClass().getSimpleName() + ")");
    }
}
