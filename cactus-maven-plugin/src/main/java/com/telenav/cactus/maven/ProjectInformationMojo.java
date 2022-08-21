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

import com.mastfrog.concurrent.ConcurrentLinkedList;
import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static java.util.Collections.emptySet;
import static java.util.concurrent.locks.LockSupport.unpark;
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
@BaseMojoGoal("project-information")
public class ProjectInformationMojo extends BaseMojo
{
    // Comma or space separated list of things to log diagnostics for
    private static final String SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS = "cactus.diag-artifact-ids";
    // Comma or space separated list of things to log stack traces for
    private static final String SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS_STACKS = "cactus.diag-stacks-for-artifact-id";

    // Parse the system properties or use defaults
    private static final Set<String> LOG_DIAGNOSTICS_FOR = artifactIdSetFrom(
            System.getProperty(SYSTEM_PROPERTY_DIAGNOSTIC_ARTIFACT_IDS));

    private static final Set<String> PRINT_STACKS_FOR
            = combine(LOG_DIAGNOSTICS_FOR,
                    artifactIdSetFrom(System.getProperty(
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
            ParallelismDiagnosticsLogger.logDiagnostic(this, stack);
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
            return new HashSet<>(Arrays.asList(
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
                return new HashSet<>(Arrays.asList(systemPropertyOrNull.split(
                        "[, ]+")));
            }
    }

}
