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

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

import static com.telenav.cactus.maven.model.resolver.versions.VersionMatchersTest.assertAccepts;
import static com.telenav.cactus.maven.model.resolver.versions.VersionMatchersTest.assertRejects;

/**
 *
 * @author timb
 */
public class BoundTest
{
    private static final Term term = new Term("1.0.1");

//    @Test
    public void testLowerBoundEquality()
    {
        Bound bnd = new Bound(term, true, false, false, false, EnumSet.of(
                ComparisonRelation.BELOW, ComparisonRelation.EQUAL));
        assertAccepts(bnd, "1.0.1");
        assertAccepts(bnd, "1.0.0");
        assertAccepts(bnd, "0.9");
        assertRejects(bnd, "1.0.1.1");
        assertRejects(bnd, "2.0");
    }

//    @Test
    public void testLowerBoundEqualityBelow()
    {
        Bound bnd = new Bound(term, true, false, false, false, EnumSet.of(
                ComparisonRelation.BELOW, ComparisonRelation.EQUAL));
        assertAccepts(bnd, "1.0.1");
        assertAccepts(bnd, "1.0.0");
        assertAccepts(bnd, "1.0");
        assertRejects(bnd, "1.0.2");
        assertRejects(bnd, "2.0");
        assertRejects(bnd, "");
        assertAccepts(bnd, ".");
    }

//    @Test
    public void testLowerBoundInequalityBelow()
    {
        Bound bnd = new Bound(term, true, false, true, false, EnumSet.of(
                ComparisonRelation.BELOW));
        assertRejects(bnd, "1.0.1");
        assertAccepts(bnd, "1.0.0");
        assertAccepts(bnd, "1.0");
    }

    @Test
    public void testUpperBoundEquality()
    {
        Bound bnd = new Bound(term, false, true, false, false, EnumSet.of(
                ComparisonRelation.ABOVE, ComparisonRelation.EQUAL));
        assertAccepts(bnd, "1.0.1");
        assertAccepts(bnd, "1.0.2");
        assertAccepts(bnd, "2.0");
        assertAccepts(bnd, "2");
        assertAccepts(bnd, "1.0.1.1");
        assertRejects(bnd, "1.0.0");
        assertRejects(bnd, "1.0");
        assertRejects(bnd, "1");
        assertAccepts(bnd, "2.0.1");
    }

    @Test
    public void testUpperBoundInequality()
    {
        Bound bnd = new Bound(term, false, true, false, true, EnumSet.of(
                ComparisonRelation.ABOVE));
        assertRejects(bnd, "1.0.1");
        assertAccepts(bnd, "1.0.2");
        assertAccepts(bnd, "2.0");
        assertAccepts(bnd, "2");
        assertAccepts(bnd, "1.0.1.1");
        assertRejects(bnd, "1.0.0");
        assertRejects(bnd, "1.0");
        assertRejects(bnd, "1");
        assertAccepts(bnd, "2.0.1");
    }
}
