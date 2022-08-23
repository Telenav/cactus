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

import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.mojobase.BaseMojoGoal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import static com.mastfrog.util.strings.AlignedText.formatTabbed;
import static com.telenav.cactus.maven.trigger.RunPolicies.LAST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.delete;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.list;
import static java.nio.file.Files.write;
import static java.nio.file.Files.isDirectory;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.toCollection;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;
import static org.apache.maven.plugins.annotations.LifecyclePhase.PRE_SITE;
import static org.apache.maven.plugins.annotations.ResolutionScope.NONE;

/**
 * Lexakai requires that some project properties files exist in
 * $FAMILY_ROOT/documentation/lexakai/projects. This will generate stub files
 * based on information from the pom.xml file if they do not exist.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = PRE_SITE,
        requiresDependencyResolution = NONE,
        instantiationStrategy = SINGLETON,
        name = "lexakai-generate", threadSafe = true)
@BaseMojoGoal("lexakai-generate")
public final class GenerateLexakaiProjectPropertiesMojo extends BaseMojo
{
    private static final String HEAD = "#\n"
            + "# Lexakai Project Configuration\n"
            + "#\n"
            + "# See https://lexakai.org for details\n"
            + "#\n"
            + "\n"
            + "#\n"
            + "# Project\n"
            + "#\n";

    @Parameter(property = "cactus.overwrite", defaultValue = "false")
    private boolean overwrite;

    @Parameter(property = "cactus.cleanup", defaultValue = "false")
    private boolean cleanup;

    @Parameter(property = "cactus.generate.lexakai.skip", defaultValue = "false")
    private boolean skip;
    
    public GenerateLexakaiProjectPropertiesMojo()
    {
        super(LAST);
    }

    @Override
    protected void performTasks(BuildLog log, MavenProject project) throws Exception
    {
        if (skip) {
            log.info("Cactus lexakai properties generation is skipped.");
            return;
        }
        if (!"pom".equals(project.getPackaging()))
        {
            log.info(
                    "Skip lexakai project properties generation for non-pom project");
            return;
        }
        Path dir = project.getBasedir().toPath();
        Path docsDir = dir.resolve("documentation").resolve("lexakai")
                .resolve("projects");
        if (!exists(docsDir))
        {
            fail("No lexakai config dir at " + docsDir);
        }
        Map<Pom, Path> maybeGenerate = findDocsFilesForModules(project, docsDir);
        for (Map.Entry<Pom, Path> e : maybeGenerate.entrySet())
        {
            if (overwrite || !exists(e.getValue()))
            {
                String text = generateLexakaiInfoStub(e.getKey());
                log.info("Write " + e.getValue());
                if (isVerbose())
                {
                    log.info(text);
                }
                if (!isPretend())
                {
                    write(e.getValue(), text.getBytes(UTF_8),
                            WRITE, TRUNCATE_EXISTING, CREATE);
                }
            }
        }
        if (cleanup)
        {
            Set<Path> toDelete = list(docsDir).filter(file -> !isDirectory(file) && file.getFileName()
                    .toString()
                    .endsWith(".properties"))
                    .collect(toCollection(HashSet::new));
            toDelete.removeAll(maybeGenerate.values());
            for (Path del : toDelete)
            {
                log.info("Delete obsolete " + del);
                if (!isPretend())
                {
                    delete(del);
                }
            }
        }
    }

    private String generateLexakaiInfoStub(Pom pom)
    {
        StringBuilder sb = new StringBuilder(HEAD);
        sb.append("project-title").append("\t=\t").append(pom.name()).append(
                "\n");
        sb.append("project-description").append("\t=\t").append(
                formatDescription(pom.description())).append("\n");
        sb.append("project-icon\t=\ticons/diagram-32\n");
        return formatTabbed(sb.toString());
    }

    private static String formatDescription(String desc)
    {
        String[] parts = desc.split("\n");
        if (parts.length > 1)
        {
            StringBuilder sb = new StringBuilder();
            for (String part : parts)
            {
                part = part.trim();
                if (!part.isEmpty())
                {
                    if (sb.length() > 0)
                    {
                        sb.append(" ");
                    }
                    sb.append(part);
                }
            }
            return sb.toString();
        }
        return desc;
    }

    private Map<Pom, Path> findDocsFilesForModules(MavenProject prj,
            Path docsDir)
    {
        Map<Pom, Path> result = new HashMap<>();
        Pom.from(prj.getFile().toPath()).ifPresent(pom ->
        {
            result.put(pom, docsDir.resolve(
                    prj.getArtifactId() + ".properties"));
        });

        for (String m : prj.getModules())
        {
            Path pomFile = prj.getBasedir().toPath().resolve(m).resolve(
                    "pom.xml");
            if (exists(pomFile))
            {
                Pom.from(pomFile).ifPresent(pom ->
                {
                    Path docFile = docsDir.resolve(
                            pom.artifactId() + ".properties");
                    result.put(pom, docFile);
                });
            }
        }
        return result;
    }
}
