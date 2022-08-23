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

import com.telenav.cactus.maven.mojobase.BaseMojoGoal;

import static com.telenav.cactus.maven.trigger.RunPolicies.FAMILY_ROOTS;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.PER_LOOKUP;
import static org.apache.maven.plugins.annotations.LifecyclePhase.POST_SITE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Variation of the copy-javadoc mojo which only copies aggregated javadoc for pom projects.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = POST_SITE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = PER_LOOKUP,
        name = "copy-aggregated-javadoc", threadSafe = true)
@BaseMojoGoal("copy-aggregated-javadoc")
public class CopyAggregatedJavadocMojo extends CopyJavadocMojo
{
    public CopyAggregatedJavadocMojo()
    {
        super(FAMILY_ROOTS);
    }
}
