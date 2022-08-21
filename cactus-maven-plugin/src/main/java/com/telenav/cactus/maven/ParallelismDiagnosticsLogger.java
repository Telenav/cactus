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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.concurrent.ConcurrentLinkedList.fifo;
import static java.lang.Runtime.getRuntime;
import static java.lang.Thread.NORM_PRIORITY;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.interrupted;

/**
 * General purpose diagnostic logging of information about the build and stack
 * traces from it, in order to track down multithreaded-build bugs; takes pains
 * not to use any data structures that can even briefly block the thread adding
 * the diagnostic, as that can impact liveness and the very parallel behavior
 * we're trying to debug.
 */
final class ParallelismDiagnosticsLogger
{
    // Singleton instance
    private static final ParallelismDiagnosticsLogger LOG = new ParallelismDiagnosticsLogger();

    // An easily grepped / searched for line prefix
    private static final String PREFIX = "\n╟";
    // Our diagnostic printing thread
    private final Thread thread = new Thread(this::diagnosticPrintingLoop);
    // Shutdown hook thread to ensure we cannot exit without having
    // printed all pending diagnostic records
    private final Thread shutdownThread = new Thread(this::onShutdown);
    // A lock-free atomic list we can push diagnostics into and pull them out of
    private final ConcurrentLinkedList<Diagnostic> diagnostics = fifo();
    // Don't start the logging thread unless we are used
    private final AtomicBoolean started = new AtomicBoolean();
    // Prefix each logged line with an index to make differentiation
    // easier
    private final AtomicInteger diagnosticsPrinted = new AtomicInteger();
    // Flag to set in a shutdown hook
    private volatile boolean shuttingDown;

    private ParallelismDiagnosticsLogger()
    {
        thread.setDaemon(true);
        // Use a lower thread priority to help avoid interfering with the
        // parallelism of the work we're trying to diagnose - there's nothing
        // like a heisenbug that disappears when you add logging to diagnose it.
        thread.setPriority(NORM_PRIORITY - 1);
    }

    public synchronized static void logDiagnostic(BaseMojo mojo,
            boolean captureStackTrace)
    {
        MavenSession sess = mojo.session();
        if (sess == null)
        {
            System.err.println("NULL SESSION IN " + mojo);
            return;
        }
        // Well THIS is a race.
        MavenProject runningProject = sess.getCurrentProject();
        if (runningProject == null)
        {
            runningProject = mojo.project();
            if (runningProject == null)
            {
                System.err.println(
                        "SESSION AND MOJO HAVE NULL PROJECT: " + mojo + " sess " + sess);
                return;
            }
            else
            {
                System.err.println(
                        "SESSION SHOWS NULL CURRENT PROJECT BUT MOJO HAS " + runningProject);
            }
        }
        LOG.add(new Diagnostic(mojo, sess, runningProject, captureStackTrace));
    }

    private void add(Diagnostic diag)
    {
        if (started.compareAndSet(false, true))
        {
            thread.start();
            // Ensure we CANNOT exit without printing any pending
            // diagnostics
            getRuntime().addShutdownHook(shutdownThread);
        }
        diagnostics.push(diag);
        LockSupport.unpark(thread);
    }

    private void diagnosticPrintingLoop()
    {
        try
        {
            while (!interrupted() && !shuttingDown)
            {
                printDiagnostics();
                LockSupport.park(this);
            }
        }
        finally
        {
            // Ensure any pending diagnostics are printed on
            // exit
            printDiagnostics();
        }
    }

    private void printDiagnostics()
    {
        while (!diagnostics.isEmpty())
        {
            diagnostics.drain(diagnostic ->
            {
                int item = diagnosticsPrinted.incrementAndGet();
                System.out.println(diagnostic.render(item));
            });
        }
    }

    private void onShutdown()
    {
        // The interrupted flag is not persistent; this is:
        shuttingDown = true;
        try
        {
            // Double-plus ensure that the diagnostics loop thread
            // has really exited
            if (thread.isAlive())
            {
                LockSupport.unpark(thread);
                thread.interrupt();
            }
            try
            {
                // Block shutdown until the print loop is really done
                thread.join();
            }
            catch (InterruptedException ex)
            {
                // irrelevant
            }
        }
        finally
        {
            // And do a final pass, which will probably find nothing to
            // print, for good measure, to catch any stragglers
            printDiagnostics();
        }
    }

    /**
     * Captures the state of the build at the time of its creation, the thread
     * it was called on, the set of things being built, and optional stack
     * trace, whether or not the build is a parallel build, and if, in the list
     * of projects Maven thinks it is building, there are duplicates, either of
     * the project object, or a different instance that has the same artifact
     * id.F
     */
    private static final class Diagnostic
    {
        private final boolean parallel;
        private final long threadId;
        private final String invokedAgainstProject;
        private final String stackTrace;
        private final String projectList;
        private final boolean duplicateIds;
        private final boolean duplicateProjects;
        private final String mojoClass;

        Diagnostic(BaseMojo mojo, MavenSession sess, MavenProject currentProject,
                boolean stack)
        {
            mojoClass = mojo.getClass().getSimpleName()
                        + " @ " + System.identityHashCode(mojo);
            threadId = currentThread().getId();
            parallel = sess.isParallel();
            invokedAgainstProject = sess.getTopLevelProject().getArtifactId();
            if (stack)
            {
                stackTrace = '\n' + Strings.toString(new Exception(
                        currentProject.getArtifactId() + " on " + mojoClass));
            }
            else
            {
                stackTrace = "";
            }
            // Create a string artifact-id list with >>> and <<< bracketing the currently
            // being built project and *** bracketing the top level project
            MavenProject top = sess.getTopLevelProject();
            List<MavenProject> all = sess.getAllProjects();
            Set<MavenProject> allMavenProjects = new HashSet<>();
            Set<String> allIdentifiers = new HashSet<>();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < all.size(); i++)
            {
                MavenProject project = all.get(i);
                allMavenProjects.add(project);
                allIdentifiers.add(project.getArtifactId() + ":" + project
                        .getGroupId());
                String prefix = PREFIX + "  " + (i + 1) + ". ";
                String suffix = "";
                if (project == top)
                {
                    prefix = (PREFIX + "  *** " + (i + 1) + ". ");
                    suffix = " ***";
                }
                if (project == currentProject)
                {
                    prefix = PREFIX + "  >---> " + (i + 1) + ". ";
                    suffix = " <---<";
                }
                sb.append(prefix).append(project.getArtifactId()).append(suffix);
            }
            projectList = sb.toString();
            duplicateIds = allIdentifiers.size() < all.size();
            duplicateProjects = allMavenProjects.size() < all.size();
        }

        public String render(int index)
        {
            return PREFIX
                    + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━ "
                    + index
                    + " ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                    + PREFIX
                    + ' '
                    + mojoClass
                    + " for "
                    + invokedAgainstProject
                    + " on " + threadId
                    + (parallel
                       ? " parallel-build "
                       : " single-threaded-build ")
                    + (duplicateProjects
                       ? " DUPLICATE_PROJECTS! "
                       : "")
                    + (duplicateIds
                       ? " DUPLICATE_PROJECT_IDENTIFIERS! "
                       : "") + projectList + stackTrace;
        }
    }

}
