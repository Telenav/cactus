package com.telenav.cactus.maven;

import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.tree.ProjectTree;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * @author jonathanl (shibo)
 */
@SuppressWarnings({ "unused", "DuplicatedCode" })
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.VALIDATE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "replace", threadSafe = true)
public class ReplaceMojo extends BaseMojo
{
    private static final Pattern REPLACE_EXPRESSION = Pattern.compile("<!--\\s*\\[(?<variable>[A-Za-z\\d]+)\\]\\s*-->");

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

    private static class TagReplacement extends Replacement
    {
        TagReplacement(String tagName, String value)
        {
            super("<" + tagName + ">.*?</" + tagName + ">",
                    "<" + tagName + ">" + value + "</" + tagName);
        }
    }

    @Parameter(property = "telenav.scope", defaultValue = "FAMILY")
    private String scopeProperty;

    @Parameter(property = "telenav.update-root", defaultValue = "true")
    private boolean updateRoot;

    @Parameter(property = "telenav.family")
    private String family;

    @Parameter(property = "telenav.pretend", defaultValue = "false")
    private boolean pretend;

    private Scope scope;

    private GitCheckout myCheckout;

    private final Map<String, Replacement> variables = new HashMap<>();

    @Override
    protected boolean isOncePerSession()
    {
        return true;
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        ProjectTree.from(project).ifPresent(tree ->
        {
            Set<GitCheckout> checkouts = PushMojo.checkoutsForScope(scope, tree,
                    myCheckout, updateRoot, family());

            var dirty = false;
            for (var checkout : checkouts)
            {
                var pom = Pom.from(checkout.checkoutRoot());
                if (pom.isPresent())
                {
                    variables.put("group-id", new TagReplacement("groupId", pom.get().coords.groupId));
                    variables.put("artifact-id", new TagReplacement("artifactId", pom.get().coords.artifactId));
                    variables.put("version", new TagReplacement("version", pom.get().coords.version));
                    if (checkout.branch().isPresent())
                    {
                        variables.put("branch-name", new Replacement("(master|develop|feature/.+|hotfix/.+)", checkout.branch().get()));
                    }
                }

                if (!pretend)
                {
                    try (var walk = Files.walk(checkout.checkoutRoot()))
                    {
                        walk.filter(path -> path.toFile().isFile()).forEach(file ->
                        {
                            if (file.endsWith(".md") || file.getFileName().equals(Paths.get("pom.xml")))
                            {
                                replaceIn(file);
                            }
                        });
                    }
                }
            }
        });
    }

    @Override
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        super.validateParameters(log, project);
        scope = Scope.find(scopeProperty);
        Optional<GitCheckout> checkout = GitCheckout.repository(project.getBasedir());
        if (checkout.isEmpty())
        {
            throw new MojoExecutionException(project.getBasedir()
                    + " does not seem to be part of a git checkout.");
        }
        myCheckout = checkout.get();
    }

    private String family()
    {
        return this.family == null || this.family.isEmpty()
                ? project().getGroupId()
                : this.family;
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
                throw new RuntimeException("Cannot find replace-next variable: " + variable);
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
                System.out.println(file + " replaced: " + replaced);
                // Files.writeString(file, replaced, StandardOpenOption.WRITE);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
