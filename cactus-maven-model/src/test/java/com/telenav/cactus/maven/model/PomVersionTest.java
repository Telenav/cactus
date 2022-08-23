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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.telenav.cactus.maven.model.VersionChangeMagnitude.DOT;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.MAJOR;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.MINOR;
import static com.telenav.cactus.maven.model.VersionChangeMagnitude.NONE;
import static com.telenav.cactus.maven.model.VersionFlavorChange.TO_RELEASE;
import static com.telenav.cactus.maven.model.VersionFlavorChange.TO_SNAPSHOT;
import static com.telenav.cactus.maven.model.VersionFlavorChange.UNCHANGED;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author timb
 */
public class PomVersionTest
{
    private static final String RELEASE_1_6_1 = "1.6.1";
    private static final String SNAPSHOT_1_5_0 = "1.5.0-SNAPSHOT";
    private static final String DEV_2_3_3 = "2.3.3-dev";

    private static final String RELEASE_1_6 = "1.6";
    private static final String RELEASE_1 = "1";
    private static final String SNAPSHOT_2 = "2-SNAPSHOT";
    private static final String DEV_3 = "3-dev";
    private static final String BIG = "13.20.141";

    private static VersionAssertions assertions = new VersionAssertions();

    @Test
    public void testEquals() {
        assertEquals(PomVersion.of("1.4.12"), PomVersion.of("1.4.12"));
        assertNotEquals(PomVersion.of("1.4.12"), PomVersion.of("1.4.13"));
        assertNotEquals(PomVersion.of("1.4.12"), PomVersion.of("1.4"));
        assertEquals(PomVersion.of("1.4.12").decimals(), Arrays.asList(1L, 4L, 12L));
    }
    
    @ParameterizedTest
    @MethodSource("assertions")
    public void test(VersionAssertions.VersionAssertion assertion)
    {
        assertion.test();
    }

    private static Stream<VersionAssertions.VersionAssertion> assertions()
    {
        return assertions.run.stream();
    }

    @BeforeAll
    public static void setup()
    {
        assertions
                // release dot changes
                .asserting(RELEASE_1_6_1, MAJOR, UNCHANGED, "2.0.0")
                .asserting(RELEASE_1_6_1, MINOR, UNCHANGED, "1.7.0")
                .asserting(RELEASE_1_6_1, DOT, UNCHANGED, "1.6.2")
                // snapshot dot changes
                .asserting(SNAPSHOT_1_5_0, MAJOR, UNCHANGED, "2.0.0-SNAPSHOT")
                .asserting(SNAPSHOT_1_5_0, MINOR, UNCHANGED, "1.6.0-SNAPSHOT")
                .asserting(SNAPSHOT_1_5_0, DOT, UNCHANGED, "1.5.1-SNAPSHOT")
                // dev dot changes
                .asserting(DEV_2_3_3, MAJOR, UNCHANGED, "3.0.0-dev")
                .asserting(DEV_2_3_3, MINOR, UNCHANGED, "2.4.0-dev")
                .asserting(DEV_2_3_3, DOT, UNCHANGED, "2.3.4-dev")
                // no changes
                .assertingNoChange(RELEASE_1_6_1, NONE, UNCHANGED)
                .assertingNoChange(SNAPSHOT_1_5_0, NONE, UNCHANGED)
                .assertingNoChange(DEV_2_3_3, NONE, UNCHANGED)
                // 
                // release dot changes to snapshot
                .asserting(RELEASE_1_6_1, MAJOR, TO_SNAPSHOT, "2.0.0-SNAPSHOT")
                .asserting(RELEASE_1_6_1, MINOR, TO_SNAPSHOT, "1.7.0-SNAPSHOT")
                .asserting(RELEASE_1_6_1, DOT, TO_SNAPSHOT, "1.6.2-SNAPSHOT")
                // yes, 1.6.2 - going from release to snapshot MUST increment the version
                // implicitly
                .asserting(RELEASE_1_6_1, NONE, TO_SNAPSHOT, "1.6.2-SNAPSHOT")
                // snapshot dot changes to snapshot
                .asserting(SNAPSHOT_1_5_0, MAJOR, TO_SNAPSHOT, "2.0.0-SNAPSHOT")
                .asserting(SNAPSHOT_1_5_0, MINOR, TO_SNAPSHOT, "1.6.0-SNAPSHOT")
                .asserting(SNAPSHOT_1_5_0, DOT, TO_SNAPSHOT, "1.5.1-SNAPSHOT")
                .assertingNoChange(SNAPSHOT_1_5_0, NONE, TO_SNAPSHOT)
                // dev dot changes to snapshot
                .asserting(DEV_2_3_3, MAJOR, TO_SNAPSHOT, "3.0.0-SNAPSHOT")
                .asserting(DEV_2_3_3, MINOR, TO_SNAPSHOT, "2.4.0-SNAPSHOT")
                .asserting(DEV_2_3_3, DOT, TO_SNAPSHOT, "2.3.4-SNAPSHOT")
                .asserting(DEV_2_3_3, NONE, TO_SNAPSHOT, "2.3.3-SNAPSHOT")
                // release dot changes to release
                .asserting(RELEASE_1_6_1, MAJOR, TO_RELEASE, "2.0.0")
                .asserting(RELEASE_1_6_1, MINOR, TO_RELEASE, "1.7.0")
                .asserting(RELEASE_1_6_1, DOT, TO_RELEASE, "1.6.2")
                .assertingNoChange(RELEASE_1_6_1, NONE, TO_RELEASE)
                // snapshot dot changes to release
                .asserting(SNAPSHOT_1_5_0, MAJOR, TO_RELEASE, "2.0.0")
                .asserting(SNAPSHOT_1_5_0, MINOR, TO_RELEASE, "1.6.0")
                .asserting(SNAPSHOT_1_5_0, DOT, TO_RELEASE, "1.5.1")
                .asserting(SNAPSHOT_1_5_0, NONE, TO_RELEASE, "1.5.0")
                // dev dot changes to release
                .asserting(DEV_2_3_3, MAJOR, TO_RELEASE, "3.0.0")
                .asserting(DEV_2_3_3, MINOR, TO_RELEASE, "2.4.0")
                .asserting(DEV_2_3_3, DOT, TO_RELEASE, "2.3.4")
                .asserting(DEV_2_3_3, NONE, TO_RELEASE, "2.3.3")
                // And test foreshortened versions
                .asserting(RELEASE_1_6, MAJOR, TO_SNAPSHOT, "2.0-SNAPSHOT")
                .asserting(RELEASE_1_6, MINOR, TO_SNAPSHOT, "1.7-SNAPSHOT")
                .asserting(RELEASE_1_6, DOT, TO_SNAPSHOT, "1.6.1-SNAPSHOT")
                // yes, 1.6.1-SNAPSHOT - going from release to snapshot MUST increment the version
                // implicitly.  We are going from effectively 1.6.0 minus the 0 - 
                // If we're adding a decimal, it makes sense for it to be a 1
                .asserting(RELEASE_1_6, NONE, TO_SNAPSHOT, "1.6.1-SNAPSHOT") // yes, 1.6.1
                // and single digits
                .asserting(RELEASE_1, MAJOR, UNCHANGED, "2")
                .asserting(RELEASE_1, MAJOR, TO_RELEASE, "2")
                .asserting(RELEASE_1, MAJOR, TO_SNAPSHOT, "2-SNAPSHOT")
                // and mv snapshot
                .asserting(SNAPSHOT_2, MAJOR, TO_SNAPSHOT, "3-SNAPSHOT")
                .asserting(SNAPSHOT_2, MAJOR, TO_RELEASE, "3")
                .asserting(SNAPSHOT_2, MAJOR, UNCHANGED, "3-SNAPSHOT")
                .asserting(DEV_3, MAJOR, TO_SNAPSHOT, "4-SNAPSHOT")
                .asserting(DEV_3, MAJOR, TO_RELEASE, "4")
                .asserting(DEV_3, NONE, TO_RELEASE, "3.0.0")
                .asserting(DEV_3, MAJOR, UNCHANGED, "4-dev")
                // and single digits minor
                .asserting(RELEASE_1, MINOR, UNCHANGED, "1.1")
                .asserting(RELEASE_1, MINOR, TO_RELEASE, "1.1")
                .asserting(RELEASE_1, MINOR, TO_SNAPSHOT, "1.1-SNAPSHOT")
                // And ensure no off-by-ones in multi-digit numbers
                .asserting(BIG, DOT, UNCHANGED, "13.20.142")
                .asserting(BIG, MINOR, UNCHANGED, "13.21.0")
                .asserting(BIG, MAJOR, UNCHANGED, "14.0.0")
                // Garbage in, something out:
                .asserting("33wurbles1", MINOR, TO_SNAPSHOT, "33.1-SNAPSHOT");
                // That is likely sufficient
    }

    private static final class VersionAssertions
    {
        private final List<VersionAssertion> run = new ArrayList<>();

        public VersionAssertions assertingNoChange(String input,
                VersionChangeMagnitude mag, VersionFlavorChange change)
        {
            return asserting(input, mag, change, null);

        }

        public VersionAssertions asserting(String input,
                VersionChangeMagnitude mag, VersionFlavorChange change,
                String output)
        {
            run.add(new VersionAssertion(input, mag, change, output));
            return this;
        }

        static class VersionAssertion
        {
            private final String input;
            private final VersionChangeMagnitude magnitude;
            private final VersionFlavorChange change;
            private final String output;

            VersionAssertion(String input,
                    VersionChangeMagnitude magnitude, VersionFlavorChange change,
                    String outputText)
            {
                this.input = input;
                this.magnitude = magnitude;
                this.change = change;
                this.output = outputText;
            }

            @Override
            public String toString()
            {
                return "'" + input + " > " + magnitude + ", " + change
                        + (output == null
                           ? " changes nothing."
                           : " -> '" + output + "'");
            }

            void test()
            {
                PomVersion ver = PomVersion.of(input);
                assertEquals(ver.text(), input);
                assertEquals(ver.toString(), input);
                Optional<PomVersion> changed = ver
                        .updatedWith(magnitude, change);
                if (output == null)
                {
                    assertTrue(changed.isEmpty(),
                            () -> "Should not get a value but got " + changed
                                    .get() + " for " + this);
                }
                else
                {
                    PomVersion expected = PomVersion.of(output);
                    assertEquals(expected, changed.get(),
                            "Wrong output for " + this);
                }
            }
        }
    }
}
