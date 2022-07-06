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

import com.telenav.cactus.maven.mojobase.BaseMojo;
import org.apache.maven.project.MavenProject;

/**
 * Policy that determines whether a BaseMojo's execution method should be run.
 * We have a lot of mojos which operate on one or more git checkouts, not
 * per-project, and either need to be run at the start or end of a build.
 * <p>
 * Many of our mojos are more associated with git repositories than with
 * projects, and should be run at most once per-repository, per-build, or only
 * against specific projects such as those in Git submodule roots. RunPolicy
 * provides the control needed to get granular control over what to run against
 * without polluting every mojo class with such logic.
 * </p>
 *
 * @see RunPolicies
 * @author Tim Boudreau
 */
public interface RunPolicy
{

    /**
     * Determine if the mojo's execution logic should be performed on the
     * particular project passed in, on behalf of the passed mojo.
     *
     * @param mojo A mojo
     * @param invokedOn The project it is being invoked against
     * @return Whether or not to proceed.
     */
    boolean shouldRun(BaseMojo mojo, MavenProject invokedOn);

    default RunPolicy and(RunPolicy other)
    {
        // For logging purposes, we need a reasonable implementation of
        // toString(), so use a concrete class.
        return new AndedRunPolicy(this, other);
    }

    default RunPolicy or(RunPolicy other)
    {
        // For logging purposes, we need a reasonable implementation of
        // toString(), so use a concrete class.
        return new OrRunPolicy(this, other);
    }

    default RunPolicy negate()
    {
        // For logging purposes, we need a reasonable implementation of
        // toString(), so use a concrete class.
        return new NegatedRunPolicy(this);
    }

    /**
     * Create an instance that only runs on a specific packaging type such as
     * "maven-plugin" or "nbm".
     *
     * @param packaging The packaging type
     * @return A RunPolicy
     */
    static RunPolicy forPackaging(String packaging)
    {
        return new PackagingSpecificRunPolicy(packaging);
    }
}
