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

import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import com.telenav.cactus.maven.trigger.RunPolicy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.writeString;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.emptyMap;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.VALIDATE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Replaces things.
 *
 * @author jonathanl (shibo)
 */
@SuppressWarnings(
        {
                "unused", "DuplicatedCode"
        })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = VALIDATE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "replace", threadSafe = true)
@BaseMojoGoal("replace")
public class ReplaceMojo extends ScopedCheckoutsMojo
{
    private static final Pattern VARIABLE_EXPRESSION = Pattern.compile(
            "<!--\\s*\\[(?<variable>[A-Za-z\\d.-]+)]\\s*-->");

    private static class ReplaceResult
    {
        String replaced;

        int count;
    }

    private static class Replacement
    {
        String pattern;

        String replacement;

        Replacement(String pattern, String replacement)
        {
            this.pattern = pattern;
            this.replacement = replacement;
        }
    }

    @Parameter(property = "cactus.replacement-version")
    String newVersion;

    @Parameter(property = "cactus.replacement-branch-name")
    String newBranchName;

    private final Map<String, Replacement> variables = new HashMap<>();

    public ReplaceMojo()
    {
    }

    public ReplaceMojo(boolean runFirst)
    {
        super(runFirst);
    }
    
    public ReplaceMojo(RunPolicy pol) {
        super(pol);
    }

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout, ProjectTree tree,
                           List<GitCheckout> checkouts) throws Exception
    {
        executeCollectingChangedFiles(log, project, myCheckout, tree, checkouts,
                emptyMap(), _ignored ->{});
    }

    void executeCollectingChangedFiles(BuildLog log, MavenProject project,
                           GitCheckout myCheckout, ProjectTree tree,
                           List<GitCheckout> checkouts, 
                           Map<GitCheckout, String> releaseBranchNames, 
                           Consumer<Path> changed) throws Exception
    {
        for (var checkout : checkouts)
        {
            var branchName = releaseBranchNames.getOrDefault(checkout, 
                    this.newBranchName == null && checkout.branch()
                    .isPresent()
                    ? checkout.branch().get()
                    : this.newBranchName);

            if (newVersion == null)
            {
                throw new RuntimeException(
                        "No replacement version was specified for " + checkout);
            }
            if (branchName == null)
            {
                throw new RuntimeException(
                        "No replacement branch name was specified and there is no default branch for " + checkout);
            }

            variables.put("cactus.replacement-version", new Replacement(
                    "\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?", newVersion));
            variables.put("cactus.replacement-branch-name", new Replacement(
                    "(develop|((release|hotfix|feature)/[a-zA-Z\\d.-]+))", branchName));

            if (!isPretend())
            {
                try (var walk = walk(checkout.checkoutRoot()))
                {
                    walk.filter(path -> path.toFile().isFile()).forEach(file ->
                    {
                        var filename = file.getFileName().toString();
                        if (filename.endsWith(".md") || file.getFileName().equals(get("pom.xml")))
                        {
                            replaceIn(file);
                            changed.accept(file);
                        }
                    });
                }
            }
        }
    }

    private ReplaceResult replace(String text)
    {
        var replaced = new StringBuilder();
        var result = new ReplaceResult();
        var count = 0;
        text.lines().forEach(line ->
        {
            var matcher = VARIABLE_EXPRESSION.matcher(line);
            if (matcher.find())
            {
                var variable = matcher.group("variable");
                var replacement = variables.get(variable);
                if (replacement == null)
                {
                    throw new RuntimeException("Cannot find replacement variable: " + variable);
                }
                result.count++;
                replaced.append(line.replaceFirst(replacement.pattern, replacement.replacement));
            }
            else
            {
                replaced.append(line);
            }
            replaced.append('\n');
        });
        result.replaced = replaced.toString();
        return result;
    }

    private void replaceIn(Path file)
    {
        try
        {
            var originalContents = readString(file, UTF_8);
            var replaced = replace(originalContents);
            if (!originalContents.equals(replaced.replaced))
            {
                if (!isPretend())
                {
                    writeString(file, replaced.replaced, UTF_8, WRITE, TRUNCATE_EXISTING);
                }
                log().info("Replaced " + replaced.count + " in " + file);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
