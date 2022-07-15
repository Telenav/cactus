package com.telenav.cactus.maven;

import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.maven.trigger.RunPolicies;
import com.telenav.cactus.metadata.BuildMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static com.telenav.cactus.util.PathUtils.home;
import static com.telenav.cactus.util.PathUtils.ifExists;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Install scripts for performing simple tasks into ~/bin or ~/.local/bin or
 * whatever is pointed to by <code>-Dcactus.script.destination</code>.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.INITIALIZE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = InstantiationStrategy.SINGLETON,
        name = "install-scripts", threadSafe = true)
@BaseMojoGoal("install-scripts")
public class InstallScriptsMojo extends BaseMojo
{
    @Parameter(property = "cactus.script.destination")
    private String destination;

    InstallScriptsMojo()
    {
        super(RunPolicies.LAST);
    }

    private Path destination() throws MojoExecutionException
    {
        Path home = home();
        return ifExists(home.resolve("bin"))
                .or(() -> ifExists(home.resolve("local").resolve("bin")))
                .orElseThrow(
                        () -> new MojoExecutionException(
                                "No ~/bin or ~/.local/bin directory to install into.  Pass -Dcactus.script.destination for a path to some directory you have write access to which is on your path"));
    }

    @Override
    protected void validateParameters(BuildLog log, MavenProject project) throws Exception
    {
        destination();
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        Path dest = destination();
        if (!Files.exists(dest))
        {
            Files.createDirectories(dest);
        }
        BuildMetadata meta = BuildMetadata.of(InstallScriptsMojo.class);
        String ver = meta.projectProperties().get("project-version");
        if (ver == null)
        {
            fail("No version found in " + meta.projectProperties());
        }
        StringBuilder msg = new StringBuilder();
        PrintMessageMojo.publishMessage(msg, session(), false);
        for (Scripts script : Scripts.values())
        {
            Path scriptFile = script.install(dest, ver, log, isPretend());
            script.describe(scriptFile, msg);
        }
        msg.insert(0, "Cactus Scripts Have Been Installed\n"
                + "==================================\n\n");
    }

    private enum Scripts
    {
        COMMIT_ALL_SUBMODULES("ccm"),
        PUSH_ALL_SUBMODULES("cpush"),
        DEVELOPMENT_PREPARATION("cdev"),
        SIMPLE_BUMP_VERSION("cbump"),
        UPDATE_SCRIPTS("cactus-script-update"),
        RELEASE_ONE_PROJECT("crel");
        private final String filename;

        Scripts(String filename)
        {
            this.filename = filename;
        }

        @Override
        public String toString()
        {
            return Strings.capitalize(name().toLowerCase().replace('_', ' '))
                    + " (" + filename + ")";
        }

        private String description()
        {
            switch (this)
            {
                // Double escaping because the code expects a message embedded
                // in a pom file, where that's what it will see.
                case COMMIT_ALL_SUBMODULES:
                    return "\\tCommit all changes in all git submodules in one\n"
                            + "\\tshot, with one commit message.";

                case PUSH_ALL_SUBMODULES:
                    return "\\tPush all changes in all submodules in one shot, after\n"
                            + "\\tensuring that your local checkouts are all up-to-date.";

                case DEVELOPMENT_PREPARATION:
                    return "\\tSwitch to the 'develop' branch in all java project checkouts.";

                case UPDATE_SCRIPTS:
                    return "\\tFinds the latest version of cactus you have installed, and runs\n\\t"
                            + "its install-scripts target to update/refresh the scripts\n\\t"
                            + "you are installing right now.";

                case SIMPLE_BUMP_VERSION:
                    return "\\tBump the version of the Maven project family it is invoked "
                            + "against,\\n\\tupdating superpom properties with the new version "
                            + "but NOT UPDATING\n\\tTHE VERSIONS OF THOSE SUPERPOMS.\n\n\\t"
                            + "This is suitable for the simple case of updating the version\n\\t"
                            + "of one thing during active development, not for doing a full\n\\t"
                            + "product release.";
                case RELEASE_ONE_PROJECT:
                    return "\\tRelease a single project - whatever pom you run it against - to "
                            + "ossrh or wherever it is configured to send it.";
                default:
                    throw new AssertionError(this);
            }
        }

        public void describe(Path path, StringBuilder into)
        {
            if (into.length() > 0)
            {
                into.append("\n\n\n");
            }
            String info = toString();
            into.append(info).append('\n');
            char[] cc = new char[info.length() + 2];
            Arrays.fill(cc, '-');
            cc[cc.length - 1] = '\n';
            cc[cc.length - 2] = '\n';
            into.append(cc);
            into.append("\\t").append(path).append("\n\n");
            into.append(description());
        }

        private InputStream script()
        {
            InputStream result = InstallScriptsMojo.class.getResourceAsStream(
                    filename);
            if (result == null)
            {
                throw new IllegalStateException("No file named "
                        + filename + " on classpath adjacent to "
                        + InstallScriptsMojo.class.getName());
            }
            return result;
        }

        private Path install(Path toDir, String pluginVersion, BuildLog log,
                boolean pretend) throws IOException
        {
            try ( InputStream in = script())
            {
                String body = new String(in.readAllBytes(), US_ASCII)
                        .replaceAll("__PLUGIN_VERSION_PLACEHOLDER__",
                                pluginVersion);
                Path script = toDir.resolve(filename);
                if (!pretend)
                {
                    Files.write(script, body.getBytes(US_ASCII), CREATE,
                            TRUNCATE_EXISTING, WRITE);

                    Set<PosixFilePermission> perms = PosixFilePermissions
                            .fromString("rwxr--r--");
                    Files.setPosixFilePermissions(script, perms);
                }
                String head = pretend
                              ? "(pretend) Copied "
                              : "Copied ";
                log.info(this + ": " + head + toString() + " to " + script);
                log.debug(body);
                return script;
            }
        }
    }
}
