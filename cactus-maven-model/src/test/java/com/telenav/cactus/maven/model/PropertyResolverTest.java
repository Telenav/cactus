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
package com.telenav.cactus.maven.model;

import org.junit.jupiter.api.Test;

import static com.telenav.cactus.maven.model.property.PropertyResolver.isResolved;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author timb
 */
public class PropertyResolverTest
{

    @Test
    public void testIsResolved() {
        assertTrue(isResolved("abcd"));
        assertTrue(isResolved("1.2.3"));
        assertFalse(isResolved("some-${templated}-thing"));
        assertFalse(isResolved("${mastfrog.parent}"));
        assertTrue(isResolved("${blah${"));
        assertTrue(isResolved("}backwards${blee"));
        assertTrue(isResolved("${"));
        assertFalse(isResolved("${}"));
        assertFalse(isResolved("prefixed-${thing}"));
    }
}
