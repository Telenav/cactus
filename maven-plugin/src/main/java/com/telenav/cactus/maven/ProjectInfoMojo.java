package com.telenav.cactus.maven;

import com.telenav.cactus.build.metadata.BuildName;
import com.telenav.cactus.maven.git.GitCheckout;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.util.PathUtils;
import static java.lang.Math.max;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoFailureException;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * A mojo that simply pretty-prints what a build is going to build.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(defaultPhase = LifecyclePhase.VERIFY,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "project-info", threadSafe = true)
public class ProjectInfoMojo extends BaseMojo
{

    private static final int DEFAULT_HEADING_LINE_LENGTH = 80;
    private static final char TOP_LEFT_CORNER = '┏';
    private static final char BREAK_LEFT = '┫';
    private static final char BREAK_RIGHT = '┣';
    private static final char LINE = '━';
    private static final char TOP_RIGHT_CORNER = '┓';
    private static final char VLINE = '┃';
    private static final char BOTTOM_LEFT_CORNER = '┗';
    private static final char BOTTOM_RIGHT_CORNER = '┛';

    /**
     * The verb participle that appears in the box heading, "Building" by
     * default.
     */
    @Parameter(property = "participle", defaultValue = "Building")
    private String participle;

    /**
     * Optionally, the "build type" in the information; if unset, we attempt to
     * guess what the build type is by examining the goals we were asked to run.
     */
    @Parameter(property = "build-type")
    private String buildType;

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        System.out.println(generateInfo(project));
    }

    private CharSequence generateInfo(MavenProject project) throws MojoFailureException
    {
        StringBuilder output = new StringBuilder();
        output.append('\n');
        String name = project.getName();
        if (name == null)
        {
            name = project.getArtifactId();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Build-Folder", buildFolderTruncated(project));
        data.put("Build-Type", buildType());
        data.put("Build-Modifiers", "Mwah-ha-ha"); // what is this?

        int ml = max(DEFAULT_HEADING_LINE_LENGTH, minLength(data));
        int boxWidth = heading(ml, output, participle, name, getBuildName(project));
        output.append(blankBoxLine(boxWidth));
        centerAligned(data, output, boxWidth);
        output.append('\n');
        output.append(blankBoxLine(boxWidth));
        output.append(closeBox(boxWidth));
        return output;
    }

    private static String buildFolderTruncated(MavenProject prj)
    {
        Path home = PathUtils.home();
        Path projectDir = prj.getBasedir().toPath();
        if (projectDir.startsWith(home))
        {
            return "~/" + home.relativize(projectDir);
        }
        return projectDir.toString();
    }

    private static String closeBox(int boxWidth)
    {
        char[] c = filled(LINE, boxWidth + 2);
        c[0] = BOTTOM_LEFT_CORNER;
        c[c.length - 2] = BOTTOM_RIGHT_CORNER;
        c[c.length - 1] = '\n';
        return new String(c);
    }

    private static String blankBoxLine(int boxWidth)
    {
        if (boxWidth <= 2)
        {
            return "";
        }
        char[] c = filled(' ', boxWidth + 2);
        c[0] = VLINE;
        c[c.length - 2] = VLINE;
        c[c.length - 1] = '\n';
        return new String(c);
    }

    private String buildType() throws MojoFailureException
    {
        if (buildType != null)
        {
            return buildType;
        }
        String result = System.getenv("BUILD_TYPE");
        if (result == null)
        {
            result = deriveBuildType();
        }
        return result;
    }

    private String deriveBuildType() throws MojoFailureException
    {
        MavenSession sess = session();
        MavenExecutionRequest exeRequest = sess.getRequest();
        List<String> goals = exeRequest.getGoals().stream().map(
                ProjectInfoMojo::trimGoal)
                .collect(Collectors.toCollection(ArrayList::new));
        // Pending: Do a better job deriving build type from the contents of the set of
        // goals.
        if (goals.contains("deploy"))
        {
            return "deploy-ossrh";
        }
        if (goals.contains("install"))
        {
            return "deploy-local";
        }
        if (goals.equals(Arrays.asList("javadoc")))
        {
            return "javadoc";
        }
        return join(", ", goals);
    }

    private static String trimGoal(String goal)
    {
        int ix = goal.lastIndexOf(':');
        if (ix > 0 && ix < goal.length() - 1)
        {
            return goal.substring(ix + 1);
        }
        return goal;
    }

    private String getBuildName(MavenProject project)
    {
        ZonedDateTime when = GitCheckout.repository(
                project.getBasedir()).flatMap(checkout ->
        {
            return checkout.commitDate();
        }).orElseGet(ZonedDateTime::now);
        return BuildName.name(when);
    }

    private static int minLength(Map<String, Object> what)
    {
        int result = 0;
        for (Map.Entry<String, Object> e : what.entrySet())
        {
            int amt = 4 + e.getKey().length();
            amt += Objects.toString(e.getValue()).length();
            result = max(result, amt);
        }
        return result + 8;
    }

    private static int heading(int targetHeadingLineLength, StringBuilder into, String... items)
    {
        newlineIfNeeded(into);
        int start = into.length();
        int neededLength = 2;
        for (int i = 0; i < items.length; i++)
        {
            neededLength += items[i].length();
            if (i < items.length - 1)
            {
                neededLength++;
            }
        }
        int leading = (targetHeadingLineLength / 2) - (neededLength / 2);
        int trailing = targetHeadingLineLength - (leading + neededLength);
        into.append(TOP_LEFT_CORNER);
        char[] line = new char[max(1, leading - 1)];
        Arrays.fill(line, LINE);
        into.append(line);
        into.append(BREAK_LEFT);
        into.append(' ');
        for (int i = 0; i < items.length; i++)
        {
            into.append(items[i]);
            into.append(' ');
        }
        into.append(BREAK_RIGHT);
        line = new char[max(1, trailing - 1)];
        Arrays.fill(line, LINE);
        into.append(line);
        into.append(TOP_RIGHT_CORNER);
        into.append('\n');
        return max(targetHeadingLineLength, (into.length() - 2) - start);
    }

    private static void centerAligned(Map<String, Object> contents, StringBuilder into, int boxWidth)
    {
        newlineIfNeeded(into);
        int maxLen = 0;
        for (Map.Entry<String, Object> e : contents.entrySet())
        {
            maxLen = max(e.getKey().length(), maxLen);
        }
        maxLen += 4;
        centerAligned(maxLen, contents, into, boxWidth);
    }

    private static String centerAligned(int maxLen, Map<String, Object> contents, StringBuilder into, int boxWidth)
    {
        contents.forEach((name, val) ->
        {
            newlineIfNeeded(into);
            char[] ws = filled(' ', maxLen - name.length());
            int lineStart = into.length();
            into.append(VLINE).append(' ').append(ws).append(name).append(": ").append(val)
                    .append(' ');
            int len = into.length() - lineStart;
            int spaces = (boxWidth - (len - 1));
            if (spaces > 1)
            {
                into.append(filled(' ', spaces - 1));
            }
            into.append(VLINE);
        });
        return into.toString();
    }

    private static void newlineIfNeeded(StringBuilder into)
    {
        if (into.length() > 0 && into.charAt(into.length() - 1) != '\n')
        {
            into.append('\n');
        }
    }

    private static char[] filled(char what, int len)
    {
        if (len < 0)
        {
            return new char[0];
        }
        char[] c = new char[len];
        Arrays.fill(c, what);
        return c;
    }

    private static String join(String delim, Iterable<?> what)
    {
        StringBuilder result = new StringBuilder();
        for (Object o : what)
        {
            if (result.length() > 0)
            {
                result.append(delim);
            }
            result.append(o);
        }
        return result.toString();
    }
}
