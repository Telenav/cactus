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

import java.util.Objects;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

/**
 *
 * @author Tim Boudreau
 */
public interface RunPolicy
{

    boolean shouldRun(MavenProject invokedOn, MavenSession session);

    default RunPolicy and(RunPolicy other)
    {
        return (prj, sess) ->
        {
            return other.shouldRun(prj, sess) && shouldRun(prj, sess);
        };
    }

    default RunPolicy or(RunPolicy other)
    {
        return (prj, sess) ->
        {
            return other.shouldRun(prj, sess) || shouldRun(prj, sess);
        };
    }

    default RunPolicy negate()
    {
        return (prj, sess) -> !shouldRun(prj, sess);
    }

    static RunPolicy forPackaging(String packaging)
    {
        return (prj, ignored) ->
        {
            return Objects.equals(packaging, prj.getPackaging());
        };
    }
}
