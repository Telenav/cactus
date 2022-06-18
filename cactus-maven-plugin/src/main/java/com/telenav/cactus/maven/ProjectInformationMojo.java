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

import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.log.BuildLog;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * A mojo that simply pretty-prints what a build is going to build.
 *
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.VERIFY,
                                           requiresDependencyResolution = ResolutionScope.NONE,
                                           instantiationStrategy = SINGLETON,
                                           name = "project-information", threadSafe = true)
public class ProjectInformationMojo extends BaseMojo
{
    @Override
    protected void performTasks(BuildLog log, MavenProject project)
    {
        System.out.println(generateInfo(project));
    }

    private CharSequence generateInfo(MavenProject project)
    {
        return "┋ Building " + project.getName();
    }
}
