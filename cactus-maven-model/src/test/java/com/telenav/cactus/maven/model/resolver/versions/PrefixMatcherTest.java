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

import static com.telenav.cactus.maven.model.resolver.versions.VersionMatchersTest.assertAccepts;
import static com.telenav.cactus.maven.model.resolver.versions.VersionMatchersTest.assertRejects;

/**
 *
 * @author timb
 */
public class PrefixMatcherTest
{

    @Test
    public void testPrefixen() {
        PrefixMatcher pm = new PrefixMatcher("1.0");
        assertAccepts(pm, "1.0.1");
        assertAccepts(pm, "1.0.1.1.1");
        assertAccepts(pm, "1.0.1.1.");
        assertAccepts(pm, "1.0");
        assertRejects(pm, "1.2");
        assertRejects(pm, "0.9");
        assertRejects(pm, ".");
        assertRejects(pm, "blah");
        assertRejects(pm, "");
    }
    
}
