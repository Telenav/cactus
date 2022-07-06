package com.telenav.cactus.maven;

import com.mastfrog.util.strings.AlignedText;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.mojobase.BaseMojo;
import com.telenav.cactus.maven.trigger.RunPolicies;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.maven.plugins.annotations.InstantiationStrategy.SINGLETON;

/**
 * Lexakai demands some project properties files exist in
 * $FAMILY_ROOT/documentation/projects/lexakai. This will generate stub files
 * based on information from the pom.xml file if they do not exist.
 *
 * @author Tim Boudreau
 */
@org.apache.maven.plugins.annotations.Mojo(
        defaultPhase = LifecyclePhase.PRE_SITE,
        requiresDependencyResolution = ResolutionScope.NONE,
        instantiationStrategy = SINGLETON,
        name = "lexakai-generate", threadSafe = true)
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
        super(RunPolicies.LAST);
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
        if (!Files.exists(docsDir))
        {
            fail("No lexakai config dir at " + docsDir);
        }
        Map<Pom, Path> maybeGenerate = findDocsFilesForModules(project, docsDir);
        for (Map.Entry<Pom, Path> e : maybeGenerate.entrySet())
        {
            if (overwrite || !Files.exists(e.getValue()))
            {
                String text = generateLexakaiInfoStub(e.getKey());
                log.info("Write " + e.getValue());
                if (isVerbose())
                {
                    log.info(text);
                }
                if (!isPretend())
                {
                    Files.write(e.getValue(), text.getBytes(UTF_8),
                            WRITE, TRUNCATE_EXISTING, CREATE);
                }
            }
        }
        if (cleanup)
        {
            Set<Path> toDelete = Files.list(docsDir).filter(
                    file -> !Files.isDirectory(file) && file.getFileName()
                    .toString()
                    .endsWith(".properties"))
                    .collect(Collectors.toCollection(HashSet::new));
            toDelete.removeAll(maybeGenerate.values());
            for (Path del : toDelete)
            {
                log.info("Delete obsolete " + del);
                if (!isPretend())
                {
                    Files.delete(del);
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
        return AlignedText.formatTabbed(sb.toString());
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
            if (Files.exists(pomFile))
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
