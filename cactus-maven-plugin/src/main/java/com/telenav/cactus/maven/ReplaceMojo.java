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

import com.telenav.cactus.maven.mojobase.ScopedCheckoutsMojo;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
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
    private static final Pattern REPLACE_EXPRESSION = Pattern.compile(
            "<!--\\s*\\[(?<variable>[A-Za-z\\d]+)\\]\\s*-->");

    private static class Replacement
    {
        Pattern pattern;

        String replacement;

        Replacement(String pattern, String replacement)
        {
            this.pattern = Pattern.compile(pattern);
            this.replacement = replacement;
        }

        String replaceLast(String text)
        {
            // Walk backwards through the text,
            for (var at = 0; at < text.length(); at++)
            {
                var tail = text.substring(text.length() - 1 - at);

                // and if we're looking at our pattern,
                var matcher = pattern.matcher(tail);
                if (matcher.lookingAt())
                {
                    // the head is the text up to the place we're at,
                    var head = text.substring(at);

                    // and the tail is the text after our match.
                    tail = text.substring(matcher.end(0));

                    // Return the substituted string.
                    return head + replacement + tail;
                }
            }
            throw new RuntimeException("Could not find pattern: " + pattern);
        }
    }

    @Parameter(property = "telenav.version")
    private String version;

    @Parameter(property = "telenav.branch-name")
    private String branchName;

    private final Map<String, Replacement> variables = new HashMap<>();

    @Override
    protected void execute(BuildLog log, MavenProject project,
            GitCheckout myCheckout, ProjectTree tree,
            List<GitCheckout> checkouts) throws Exception
    {
        for (var checkout : checkouts)
        {
            var branchName = this.branchName == null && checkout.branch()
                    .isPresent()
                         ? checkout.branch().get()
                         : this.branchName;

            if (version == null)
            {
                throw new RuntimeException(
                        "No replacement version was specified for " + checkout);
            }
            if (branchName == null)
            {
                throw new RuntimeException(
                        "No replacement branch name was specified and there is no default branch for " + checkout);
            }

            variables.put("version", new Replacement(
                    "\\d+\\.\\d+(\\.\\d+)?(-SNAPSHOT)?", version));
            variables.put("branch-name", new Replacement(
                    "(master|develop|feature/.+|hotfix/.+)", branchName));

            if (!isPretend())
            {
                try ( var walk = Files.walk(checkout.checkoutRoot()))
                {
                    walk.filter(path -> path.toFile().isFile()).forEach(file ->
                    {
                        if (file.endsWith(".md") || file.getFileName().equals(
                                Paths.get("pom.xml")))
                        {
                            replaceIn(file);
                        }
                    });
                }
            }
        }
    }

    private String replace(String text)
    {
        var replaced = new StringBuilder();
        var matcher = REPLACE_EXPRESSION.matcher(text);
        while (matcher.find())
        {
            var variable = matcher.group("variable");
            var replacement = variables.get(variable);
            if (replacement == null)
            {
                throw new RuntimeException(
                        "Cannot find replace-next variable: " + variable);
            }

            replaced.append(text.substring(matcher.start()));
            replaced.append(replacement.replacement);
            text = text.substring(matcher.end());
        }
        replaced.append(text);
        return replaced.toString();
    }

    private void replaceIn(Path file)
    {
        try
        {
            var contents = Files.readString(file);
            var replaced = replace(contents);
            if (!contents.equals(replaced))
            {
                log().info(file + " replaced: " + replaced);
                if (!isPretend())
                {
                    Files.writeString(file, replaced, WRITE, TRUNCATE_EXISTING);
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
