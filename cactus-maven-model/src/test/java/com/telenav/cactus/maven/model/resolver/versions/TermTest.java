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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author timb
 */
public class TermTest
{

    @Test
    public void testToString()
    {
        Term term = new Term("1.0.1");
        assertRelation(term, ComparisonRelation.EQUAL, "1.0.1");
        assertRelation(term, ComparisonRelation.BELOW, "1.0.0");
        assertRelation(term, ComparisonRelation.ABOVE, "1.0.2");
        assertRelation(term, ComparisonRelation.ABOVE, "1.0.1.1");
        assertRelation(term, ComparisonRelation.BELOW, "1.0.0.1");
    }

    private static void assertRelation(Term term, ComparisonRelation rel,
            String with)
    {
        ComparisonRelation r = term.apply(with);
        assertEquals(rel, r,
                "Relation should be " + rel.name() + " for " + term + " with " + with
                + " but got " + r.name());
    }

}
