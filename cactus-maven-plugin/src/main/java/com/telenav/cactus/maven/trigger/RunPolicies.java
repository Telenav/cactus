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
package com.telenav.cactus.maven.trigger;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 * Common implementations of RunPolicy.
 *
 * @author Tim Boudreau
 */
public enum RunPolicies implements RunPolicy
{
    /**
     * Run on first invocation.
     */
    FIRST,
    /**
     * Run against every project.
     */
    EVERY,
    /**
     * Run on every POM packaging project.
     */
    POM_PROJECT_ONLY,
    /**
     * Run only on projects that do NOT have packaging POM.
     */
    NON_POM_PROJECT_ONLY,
    /**
     * Run when invoked against the last project (the one maven was initially
     * run against in the case of an aggregator project).
     */
    LAST;

    /**
     * Determine if the mojo should run.
     *
     * @param invokedOn The target project
     * @param session The session
     * @return True if it should run
     */
    @Override
    public boolean shouldRun(MavenProject invokedOn, MavenSession session)
    {
        switch (this)
        {
            case FIRST:
                return isFirst(invokedOn, session);
            case POM_PROJECT_ONLY:
                return "pom".equals(invokedOn.getPackaging());
            case NON_POM_PROJECT_ONLY:
                return !"pom".equals(invokedOn.getPackaging());
            case EVERY:
                return true;
            case LAST:
                return isLast(invokedOn, session);
            default:
                throw new AssertionError(this);

        }
    }

    private static boolean isFirst(MavenProject invokedOn, MavenSession session)
    {
        // XXX verify that this actually always works - if the first project
        // invoked does not use this mojo, this test might fail.
        return session.getProjects().indexOf(invokedOn) == 0;
    }

    private static boolean isLast(MavenProject invokedOn, MavenSession session)
    {
        return session.getExecutionRootDirectory().equalsIgnoreCase(invokedOn
                .getBasedir().toString());
    }
}
