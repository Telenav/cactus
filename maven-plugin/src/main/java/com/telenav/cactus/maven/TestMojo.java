////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
// © 2022 Telenav, Inc.
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A Maven mojo.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        name = "do-something", threadSafe = true)
public class TestMojo extends AbstractMojo
{

    // These are magically injected by Maven:
    @Component
    MavenProject project;

    @Parameter(property = "thing", defaultValue = "not really a thing")
    private String thing;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException
    {
        System.out.println("\n--------------------- Cactus Maven Plugin Says ---------------------");
        System.out.println("You are building " + project.getGroupId() + ":"
                + project.getArtifactId() + ":" + project.getVersion());
        System.out.println("The thing is '" + thing + "'");
        System.out.println("Isn't that special?");
        System.out.println("---------------------------------------------------------------------\n");
    }
}
