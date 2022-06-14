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

package com.telenav.cactus.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public enum RunPolicies implements RunPolicy
{
    FIRST,
    EVERY,
    POM_PROJECT_ONLY,
    NON_POM_PROJECT_ONLY,
    LAST;

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
