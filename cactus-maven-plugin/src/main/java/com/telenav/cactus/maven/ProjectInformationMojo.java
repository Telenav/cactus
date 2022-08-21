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
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.util.HashSet;
import java.util.Set;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.maven.ParallelismDiagnosticsLogger.logDiagnostic;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VERIFY;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * A mojo that simply pretty-prints what a build is going to build.
 *
 * @author Tim Boudreau
 * @author jonathanl (shibo)
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = VERIFY,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "project-information", threadSafe = true)
@BaseMojoGoal("project-information")
public class ProjectInformationMojo extends BaseMojo
{
    // Comma or space separated list of things to log diagnostics for
    private static final String SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS = "cactus.diag-artifact-ids";
    // Comma or space separated list of things to log stack traces for
    private static final String SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS_STACKS = "cactus.diag-stacks-for-artifact-id";

    // Parse the system properties or use defaults
    private static final Set<String> LOG_DIAGNOSTICS_FOR = artifactIdSetFrom(getProperty(SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS));

    private static final Set<String> PRINT_STACKS_FOR
            = combine(LOG_DIAGNOSTICS_FOR,
                    artifactIdSetFrom(getProperty(
                            SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS_STACKS)));

    @Override
    protected void performTasks(BuildLog log, MavenProject project)
    {
        emitMessage(generateInfo(project));
        diagnostics(project);
    }

    private CharSequence generateInfo(MavenProject project)
    {
        return "┋ Building " + project.getName();
    }

    private void diagnostics(MavenProject project)
    {
        if (pertainsTo(project, LOG_DIAGNOSTICS_FOR))
        {
            // Avoid being too noisy unless we're told to be
            boolean stack = pertainsTo(project, PRINT_STACKS_FOR);
            // This is a little bit elaborate, but the PrintStreams associated with
            // System.out and System.err are usually synchronized, and we do not
            // want to do ANYTHING here that can impact concurrency or call order
            // of the thing we're trying to diagnose.  So, shuffle the printing of
            // the diagnostics off onto a background thread without using any kind
            // of queue that takes a lock in its add method, so we interfere as
            // little as possible with the original code flow
            logDiagnostic(this, stack);
        }
    }

    private static boolean pertainsTo(MavenProject project, Set<String> set)
    {
        return set.isEmpty()
                || set.contains(project.getName())
                || set.contains(project.getArtifactId());
    }

    private static Set<String> combine(Set<String> a, Set<String> b)
    {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return result;
    }

    private static Set<String> artifactIdSetFrom(String systemPropertyOrNull)
    {
        if (systemPropertyOrNull == null)
        {
            return new HashSet<>(asList(
                    "kivakit-serialization-properties",
                    "kivakit-testing"));
        }
        else
            if ("all".equals(systemPropertyOrNull) || systemPropertyOrNull
                    .isBlank())
            {
                return emptySet();
            }
            else
            {
                return new HashSet<>(asList(systemPropertyOrNull.split(
                        "[, ]+")));
            }
    }

}
