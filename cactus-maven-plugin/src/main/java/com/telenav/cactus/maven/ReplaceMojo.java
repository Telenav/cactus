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
import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

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
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "replace", threadSafe = true)
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
    private String newVersion;

    @Parameter(property = "cactus.replacement-branch-name")
    private String newBranchName;

    private final Map<String, Replacement> variables = new HashMap<>();

    @Override
    protected void execute(BuildLog log, MavenProject project,
                           GitCheckout myCheckout, ProjectTree tree,
                           List<GitCheckout> checkouts) throws Exception
    {
        for (var checkout : checkouts)
        {
            var branchName = this.newBranchName == null && checkout.branch()
                    .isPresent()
                    ? checkout.branch().get()
                    : this.newBranchName;

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
                try (var walk = Files.walk(checkout.checkoutRoot()))
                {
                    walk.filter(path -> path.toFile().isFile()).forEach(file ->
                    {
                        var filename = file.getFileName().toString();
                        if (filename.endsWith(".md") || file.getFileName().equals(Paths.get("pom.xml")))
                        {
                            replaceIn(file);
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
            var originalContents = Files.readString(file);
            var replaced = replace(originalContents);
            if (!originalContents.equals(replaced.replaced))
            {
                if (!isPretend())
                {
                    Files.writeString(file, replaced.replaced, WRITE, TRUNCATE_EXISTING);
                }
                System.out.println("Replaced " + replaced.count + " in " + file);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
