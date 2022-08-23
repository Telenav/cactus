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
package com.telenav.cactus.test.project;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenArtifactCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static com.telenav.cactus.test.project.GeneratedProjectTree.cactusVersion;
import static java.lang.Math.min;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * A wrapper for a generated project which can perform git and maven operations
 * over it. Provides convenience methods for quickly making modifications to
 * files, performing git operations and running maven against it.
 */
public interface ProjectWrapper extends MavenArtifactCoordinates
{
    /**
     * Get the tree
     *
     * @return
     */
    GeneratedProjectTree<?> owner();

    /**
     * Get the path to the project directory.
     *
     * @return A path
     */
    Path path();

    /**
     * Get the artifact identifiers of this project.
     *
     * @return An ArtifactIdentifiers
     */
    ArtifactIdentifiers identifiers();

    /**
     * Get the git checkout that houses this project.
     *
     * @return A git checkout
     */
    GitCheckout getCheckout();

    /**
     * Build this project using mvn clean install
     *
     * @return True if the build succeeds
     */
    default boolean build()
    {
        return runMaven("clean", "install");
    }

    boolean runMaven(String... args);

    /**
     * Need to call this to discard cached poms in case versions or similar are
     * altered. Calling it on the generated project tree will run it for all
     * instantiated project wrappers.
     */
    void pomsChanged();

    @Override
    public default GroupId groupId()
    {
        return identifiers().groupId();
    }

    @Override
    public default ArtifactId artifactId()
    {
        return identifiers().artifactId();
    }

    @Override
    public default ThrowingOptional<String> resolvedVersion()
    {
        return pom().resolvedVersion();
    }

    @Override
    public default PomVersion version()
    {
        return pom().version();
    }

    /**
     * Adds a generic comment to a pom file.
     *
     * @return true
     * @throws IOException
     */
    default boolean modifyPomFile() throws IOException
    {
        return modifyPomFile(pomText ->
        {
            int ix = pomText.indexOf(">\n");
            if (ix < 0)
            {
                throw new IllegalArgumentException(pomText);
            }
            StringBuilder sb = new StringBuilder(pomText);
            sb.insert(ix + 1, "\n<!-- a trivial comment -->\n");
            return sb.toString();
        });
    }

    default boolean modifyPomFile(String commentText) throws IOException
    {
        return modifyPomFile(pomText ->
        {
            int ix = pomText.indexOf(">\n");
            if (ix < 0)
            {
                throw new IllegalArgumentException(pomText);
            }
            StringBuilder sb = new StringBuilder(pomText);
            sb.insert(ix + 1, "\n<!-- " + commentText + " -->\n");
            return sb.toString();
        });
    }

    default boolean modifyPomFile(Function<String, String> f) throws IOException
    {
        String data = new String(Files.readAllBytes(pomFile()), UTF_8);
        String nue = f.apply(data);
        boolean result = !data.equals(nue);
        Files.write(pomFile(), nue.getBytes(UTF_8), TRUNCATE_EXISTING, WRITE);
        return result;
    }

    /**
     * Get the path to a Java source file in the generated package of this
     * project. The returned file may or may not exist.
     *
     * @param className A java class name (no extension)
     * @return A path
     */
    default Path sourceFile(String className)
    {
        return javaPackagePath().resolve(className + ".java");
    }

    default ProjectWrapper modifySourceFile(String className) throws IOException
    {
        return modifySourceFile(className, "Comment-" + System
                .currentTimeMillis());
    }

    default ProjectWrapper modifySourceFile(String className, String comment)
            throws IOException
    {
        return modifySourceFile(className, old ->
        {
            int ix = old.indexOf('{');
            StringBuilder sb = new StringBuilder(className);
            if (ix < 0)
            {
                sb.append("\n//").append(comment).append("\n");
            }
            else
            {
                sb.insert(min(ix + 1, sb.length() - 1), "\n//" + comment + "\n");
            }
            return sb.toString();
        });
    }

    default ProjectWrapper modifySourceFile(String className,
            Function<String, String> f) throws IOException
    {
        String body = new String(Files.readAllBytes(sourceFile(className)),
                UTF_8);
        String s = f.apply(body);
        if (!body.equals(s))
        {
            write(sourceFile(className), s.getBytes(UTF_8), TRUNCATE_EXISTING,
                    WRITE);
        }
        return this;
    }

    default Path newJavaSource(String className, String body) throws IOException
    {
        Path file = javaPackagePath().resolve(className + ".java");
        write(file, body.getBytes(UTF_8), CREATE, TRUNCATE_EXISTING,
                WRITE);
        return file;
    }

    default String javaPackage()
    {
        String aid = artifactId().text().replace('-', '.');
        return groupId().text() + "." + aid;
    }

    default Path javaPackagePath()
    {
        return sourceRoot()
                .resolve(javaPackage().replace('.', '/'));
    }

    default boolean runCactusTarget(String targetName, Map<String, Object> args)
    {
        List<String> strings = new ArrayList<>();
        args.forEach((n, v) ->
        {
            strings.add("-D" + n + "=" + v);
        });
        strings.add("-Dcactus.verbose=true");
        return runCactusTarget(targetName, strings.toArray(String[]::new));
    }

    default boolean runCactusTarget(String targetName, String... args)
    {
        List<String> list = new ArrayList<>(args.length + 1);
        list.addAll(Arrays.asList(args));
        String arg = "com.telenav.cactus:cactus-maven-plugin:"
                + cactusVersion() + ":" + targetName;
        list.add(arg);
        return runMaven(list.toArray(String[]::new));
    }

    default void addDependency(String group, String artifact, String ver) throws IOException
    {
        String text
                = "\n        <dependency>"
                + "\n            <groupId>" + group + "</groupId>"
                + "\n            <artifactId>" + artifact + "</artifactId>"
                + "\n            <version>" + ver + "</version>"
                + "\n         </dependency>\n";
        modifyPomFile(oldText ->
        {
            int ix = oldText.indexOf("<dependencies>");
            boolean needWrap = ix < 0;
            String finalText;
            if (needWrap)
            {
                ix = oldText.indexOf("</project>");
                finalText = "\n<dependencies>" + text + "    </dependencies>\n";
            }
            else
            {
                ix = ix + "<dependencies>".length() + 1;
                finalText = text;
            }
            StringBuilder sb = new StringBuilder(oldText);
            sb.insert(ix, finalText);
            return sb.toString();
        });
    }

    default boolean newBranch(String branchName)
    {
        return getCheckout().createAndSwitchToBranch(branchName,
                Optional.of("develop"));
    }

    default Path sourceRoot()
    {
        return path().resolve("src/main/java");
    }

    default Path pomFile()
    {
        return path().resolve("pom.xml");
    }

    default boolean commit()
    {
        return commit("Some commit");
    }

    default boolean commit(String msg)
    {
        getCheckout().addAll();
        return getCheckout().commit(msg);
    }

    default boolean push()
    {
        return getCheckout().push();
    }

    default boolean pull()
    {
        return getCheckout().pull();
    }

    default boolean pushCreatingBranch()
    {
        return getCheckout().pushCreatingBranch();
    }

    default String currentBranch()
    {
        return getCheckout().branch().orElse("-none-");
    }

    default Pom pom()
    {
        return Pom.from(pomFile()).get();
    }
}
