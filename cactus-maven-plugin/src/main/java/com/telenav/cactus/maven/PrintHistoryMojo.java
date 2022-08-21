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

import com.telenav.cactus.git.CommitInfo;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.util.Arrays.asList;
import static java.util.Arrays.fill;
import static java.util.stream.Collectors.toCollection;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.KEEP_ALIVE;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 *
 * @author timb
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = KEEP_ALIVE,
        name = "last-change", threadSafe = true)
@BaseMojoGoal("last-change")
public class PrintHistoryMojo extends ScopedCheckoutsMojo
{
    private static final int PAGE_SIZE = 5;

    @Parameter(property = "cactus.file.extension")
    String fileExtension;

    @Parameter(property = "cactus.local")
    boolean local;

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        if (local)
        {
            checkouts = asList(myCheckout);
        }
        checkouts.forEach(checkout ->
        {
            Set<Pom> nonPomProjects = tree.projectsWithin(checkout)
                    .stream().filter(pom -> !pom.isPomProject())
                    .collect(toCollection(HashSet::new));
            if (nonPomProjects.isEmpty())
            {
                return;
            }
            emitCheckoutStart(checkout);

            checkout.changeHistory(PAGE_SIZE, historyRecord ->
            {
                if (fileExtension != null)
                {
                    Optional<CommitInfo> optInfo = historyRecord
                            .includeOnlyFileExtension(fileExtension);
                    if (!optInfo.isPresent())
                    {
                        return true;
                    }
                    historyRecord = optInfo.get();
                }
                Set<Pom> found = new HashSet<>();
                for (Pom pom : nonPomProjects)
                {
                    Path relPath = checkout.checkoutRoot()
                            .relativize(pom.projectFolder());
                    if (historyRecord.contains(relPath))
                    {
                        found.add(pom);
                        emit(pom, relPath, historyRecord);
                    }
                }
                nonPomProjects.removeAll(found);
                return !nonPomProjects.isEmpty();
            });
        });
    }

    private void emitCheckoutStart(GitCheckout checkout)
    {
        emitMessage("");
        String ln = checkout.loggingName();
        emitMessage(ln);
        char[] c = new char[ln.length() + 1];
        fill(c, '=');
        c[c.length - 1] = '\n';
        emitMessage(new String(c));
    }

    private void emit(Pom pom, Path relPath, CommitInfo historyRecord)
    {
        emitMessage(
                relPath + " " + pom.toArtifactIdentifiers() + "\n  " + historyRecord
                .info());
    }
}
