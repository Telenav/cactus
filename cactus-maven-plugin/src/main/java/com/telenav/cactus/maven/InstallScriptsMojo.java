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

import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import com.telenav.cactus.metadata.BuildMetadata;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.util.streams.Streams.readResourceAsUTF8;
import static com.telenav.cactus.maven.PrintMessageMojo.publishMessage;
import static com.telenav.cactus.maven.trigger.RunPolicies.LAST;
import static com.telenav.cactus.metadata.BuildMetadata.of;
import static com.telenav.cactus.util.PathUtils.home;
import static com.telenav.cactus.util.PathUtils.ifExists;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.exists;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.INITIALIZE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Install scripts for performing simple tasks into ~/bin or ~/.local/bin or
 * whatever is pointed to by <code>-Dcactus.script.destination</code>.
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("unused")
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = INITIALIZE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "install-scripts", threadSafe = true)
@BaseMojoGoal("install-scripts")
public class InstallScriptsMojo extends BaseMojo
{
    private static final String FUNCTION_FRAGMENT_FILE_NAME
            = "run-maven-function-fragment.txt";
    private static String runMavenFunctionFragment;

    @Parameter(property = "cactus.script.destination")
    private String destination;

    @Parameter(property = "cactus.create.aliases", defaultValue = "true")
    @SuppressWarnings("FieldMayBeFinal")
    private boolean createAliases = true;

    InstallScriptsMojo()
    {
        super(LAST);
    }

    private Path destination() throws MojoExecutionException
    {
        if (destination != null && !destination.isBlank())
        {
            return Paths.get(destination.trim());
        }
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
        if (!exists(dest))
        {
            createDirectories(dest);
        }
        BuildMetadata meta = of(InstallScriptsMojo.class);
        String ver = meta.projectProperties().get("project-version");
        if (ver == null)
        {
            fail("No version found in " + meta.projectProperties());
        }
        StringBuilder msg = new StringBuilder();
        publishMessage(msg, session(), false);
        for (Scripts script : Scripts.values())
        {
            Path scriptFile = script.install(dest, createAliases, ver, log,
                    isPretend());
            script.describe(scriptFile, msg, createAliases);
        }
        msg.insert(0, "Cactus Scripts Have Been Installed\n"
                + "==================================\n\n");
    }

    private enum Scripts
    {
        COMMIT_ALL_SUBMODULES("ccm"),
        PUSH_ALL_SUBMODULES("cpush"),
        PULL_ALL_SUBMODULES("cpull"),
        DEVELOPMENT_PREPARATION("cdev"),
        CHANGE_BRANCH("cbranch"),
        SIMPLE_BUMP_VERSION("cbump"),
        LAST_CHANGE_BY_PROJECT("cch"),
        FAMILY_VERSIONS("cver"),
        RELEASE_ONE_PROJECT("crel"),
        CREATE_PULL_REQUEST("cpr"),
        UPDATE_SCRIPTS("cactus-script-update");
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

        boolean usesLoggingWrapper()
        {
            switch (this)
            {
                // A few scripts munge $@ into a single argument, and
                // the logging function, using eval, would de-quote and split
                // the message back into a list of arguments, causing failure
                // since the second word of, say, a commit-message would be
                // passed as its own argument to Maven
                case COMMIT_ALL_SUBMODULES:
                case CREATE_PULL_REQUEST:
                // And a few where either we want immediate full logging, or
                // it is irrelevant
                case RELEASE_ONE_PROJECT:
                case FAMILY_VERSIONS:
                    return false;
                default:
                    return true;
            }
        }

        private String description()
        {
            switch (this)
            {
                // Double escaping because the code expects a message embedded
                // in a pom file, where that's what it will see.
                case COMMIT_ALL_SUBMODULES:
                    return "\\tCommit all changes in all git submodules in one\n"
                            + "\\tshot, with one commit message.\n\n\\tIf `-p` or `--push` is "
                            + "the first argument, also push any committed checkouts\n"
                            + "\\t(creating a new remote branch if there is no corresponding one).";

                case PUSH_ALL_SUBMODULES:
                    return "\\tPush all changes in all submodules in one shot, after\n"
                            + "\\tensuring that your local checkouts are all up-to-date.";

                case PULL_ALL_SUBMODULES:
                    return "\\tPull changes in all submodules";

                case DEVELOPMENT_PREPARATION:
                    return "\\tSwitch to the 'develop' branch in all java project checkouts.";

                case CHANGE_BRANCH:
                    return "\\tChange branches or create a new branch.  To simply check out an existing feature branch\\n"
                            + "\\tin all projects that have it, run `cbranch feature/some-branch`.\\n\\n\\t"
                            + "To *create* a new branch, run `cbranch --new feature/some-branch`.  \\n\\t"
                            + "If a local branch with the requested name exists for one checkout, that checkout \\n\\t"
                            + "will simply be switched to it.  If a remote branch with the requeted name exists for a checkout,\\n\\t"
                            + "but a local one does not, a local tracking branch will be created and switched to for that checkout.\\n\\t"
                            + "If no local or remote branch exists for a checkout, the default development branch (develop) will be switched to."
                            + "\\n\\tBy default, the "
                            + "scope of checkouts affected will be the project family of whatever pom.xml you are running\\n\\t"
                            + "against, and any child project families of it (if run in the root of a checkout, that may be everything).\\n\\n\\t"
                            + "Pass `--all` to apply to *every* project family in your tree.\\n\\n\\t"
                            + "If not passing `--new`, any checkouts that do not have the named branch will be switched to the default development branch.";

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

                case LAST_CHANGE_BY_PROJECT:
                    return "\\tPrints git commit info about the last change that altered a java "
                            + "\n\\tsource file in a project, or with --all, the entire tree.";

                case FAMILY_VERSIONS:
                    return "\\tPrints the inferred version of each project family in the current\n\\t"
                            + "project tree.  These versions are what will be the basis used by \n\\t"
                            + "BumpVersionMojo when computing a new revision.";

                case CREATE_PULL_REQUEST:
                    return "\\tCreate a pull request.  Any arguments that are passed will be combined\n\\t"
                            + "to create the title for the pull request.\\n\\n\\t"
                            + "Typically this is run against a project which is on a feature branch; the\n\\t"
                            + "tool will scan for other git submodules on a same-named branch, committing or pushing\n\\t"
                            + "as needed, and create pull requests for each if none already exists.";
                default:
                    throw new AssertionError(this);
            }
        }

        String longName()
        {
            return "cactus-" + name().toLowerCase().replace('_', '-');
        }

        public void describe(Path path, StringBuilder into, boolean aliased)
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
            into.append("\\t").append(path).append('\n');
            if (aliased)
            {
                into.append("\\t").append(path.getParent().resolve(filename))
                        .append('\n');
            }
            into.append("\n");
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

        private String insertHelpStanza(String script, String pluginVersion)
                throws IOException
        {
            String desc = description();
            if (desc != null && !desc.isEmpty())
            {
                StringBuilder sb = new StringBuilder(
                        "\n\nif [ -n \"$1\" ]; then\n")
                        .append("    if [ '--help' = $1 ]; then\n");

                desc = desc.replaceAll("\\\\n", "\n")
                        .replaceAll("\\\\t", "\t")
                        .replaceAll("\"", "\\\"");

                sb.append("        echo '").append(longName()).append(" ")
                        .append(pluginVersion).append("' 1>&2\n");
                sb.append("        echo 1>&2\n");
                for (String line : desc.split("\n"))
                {
                    sb.append("        echo '").append(line).append("' 1>&2\n");
                }
                sb.append("         exit 0");
                sb.append("\n    fi");
                sb.append("\nfi\n");
                StringBuilder txt = new StringBuilder(script);
                int ix = script.indexOf('\n');
                if (usesLoggingWrapper()) {
                    sb.append(runMavenFunctionFragment());
                }
                txt.insert(ix + 1, sb);
                return txt.toString();
            }
            else
                if (usesLoggingWrapper())
                {
                    StringBuilder sb = new StringBuilder(script);
                    int ix = script.indexOf('\n');
                    sb.insert(ix + 1, runMavenFunctionFragment());
                    return sb.toString();
                }
            return script;
        }

        private Path install(Path toDir, boolean createAliases,
                String pluginVersion, BuildLog log,
                boolean pretend) throws IOException
        {
            try ( InputStream in = script())
            {
                String body = new String(in.readAllBytes(), US_ASCII)
                        .replaceAll("__PLUGIN_VERSION_PLACEHOLDER__",
                                pluginVersion);

                body = insertHelpStanza(body, pluginVersion);

                Path script = toDir.resolve(longName());
                Path shorthand = toDir.resolve("./" + filename);
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
                if (createAliases && this != UPDATE_SCRIPTS)
                {
                    if (Files.exists(shorthand, LinkOption.NOFOLLOW_LINKS))
                    {
                        Files.delete(shorthand);
                    }
                    Files.createSymbolicLink(shorthand, script);
                }
                return script;
            }
        }
    }

    static String runMavenFunctionFragment() throws IOException
    {
        if (runMavenFunctionFragment != null)
        {
            return runMavenFunctionFragment;
        }
        String result = runMavenFunctionFragment = readResourceAsUTF8(
                InstallScriptsMojo.class,
                FUNCTION_FRAGMENT_FILE_NAME);
        if (result == null)
        {
            throw new Error(FUNCTION_FRAGMENT_FILE_NAME + " is not adjacent to "
                    + InstallScriptsMojo.class);
        }
        return result;
    }
}
