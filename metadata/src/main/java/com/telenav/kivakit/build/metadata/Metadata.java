////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//  © 2011-2021 Telenav, Inc.
//  Licensed under Apache License, Version 2.0
//
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

package com.telenav.kivakit.build.metadata;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE;

/**
 * Metadata about the calling KivaKit project as well as a program entrypoint that creates this information when called
 * from maven during the build process.
 *
 * @author jonathanl (shibo)
 */
public class Metadata
{
    /** Start of KivaKit epoch is December 5, 2020 (blue monkey) */
    public static final int KIVAKIT_EPOCH_DAY = 18_601;

    /** Metadata for projects */
    private static final Map<Class<?>, Metadata> projectToMetadata = new HashMap<>();

    /**
     * Writes a build.properties file out to the given output folder with the following entries:
     *
     * <ul>
     *     <li>build-number - The current build number since the start of the KivaKit epoch</li>
     *     <li>build-date - The current build date as [year].[month].[day-of-month]</li>
     *     <li>build-name - The current build name</li>
     * </ul>
     *
     * <p>
     * Some KivaKit scripts read this information, as well as kivakit-core-kernel.
     * </p>
     *
     * @param arguments Output folder to write metadata to
     */
    public static void main(final String[] arguments)
    {
        if (arguments.length == 1)
        {
            try
            {
                // Get output path and ensure it exists,
                final var outputPath = Path.of(arguments[0]);
                if (!Files.isDirectory(outputPath))
                {
                    Files.createDirectory(outputPath);
                }

                // formulate the lines of the build.properties file,
                final var properties = current(null).buildProperties();
                final var lines = new ArrayList<String>();
                for (final var key : properties.keySet())
                {
                    lines.add(key + " = " + properties.get(key));
                }

                // and write them out in the output folder.
                Files.writeString(outputPath.resolve("build.properties"), String.join("\n", lines) + "\n", CREATE);
            }
            catch (final Exception cause)
            {
                throw new IllegalStateException("Unable to write metadata", cause);
            }
        }
        else
        {
            System.err.println("Usage: kivakit-metadata [output-folder]");
        }
    }

    /**
     * @param projectType A class in the caller's project for loading resources
     * @return Metadata for the given project
     */
    public static Metadata of(final Class<?> projectType)
    {
        return projectToMetadata.computeIfAbsent(projectType, ignored -> new Metadata(projectType, Type.PROJECT));
    }

    private enum Type
    {
        PROJECT,
        CURRENT
    }

    /** A class in the caller's project for loading resources */
    private final Class<?> projectType;

    /** The type of metadata */
    private final Type type;

    /** Build property map */
    private Map<String, String> buildProperties;

    /** Project property map */
    private Map<String, String> projectProperties;

    private Metadata(final Class<?> projectType, final Type type)
    {
        this.projectType = projectType;
        this.type = type;
    }

    /**
     * Retrieves the properties in the /project.properties resource, similar to this:
     *
     * <pre>
     * build-number = 104
     * build-date = 2021.03.18
     * build-name = sparkling piglet
     * </pre>
     *
     * @return The contents of the maven metadata file
     */
    public Map<String, String> buildProperties()
    {
        if (buildProperties == null)
        {
            // If we are metadata for the current build,
            if (type == Type.CURRENT)
            {
                // then use current build metadata based on the time
                final var properties = new HashMap<String, String>();
                properties.put("build-number", Integer.toString(currentBuildNumber()));
                properties.put("build-date", DateTimeFormatter.ofPattern("yyyy.MM.dd").format(currentBuildDate()));
                properties.put("build-name", BuildName.name(currentBuildNumber()));
                buildProperties = properties;
            }
            else
            {
                // otherwise, use the project's metadata.
                buildProperties = properties(metadata(projectType, "/build.properties"));
            }
        }

        return buildProperties;
    }

    /**
     * Retrieves the properties in the /build.properties resource, similar to this:
     *
     * <pre>
     * project-version=8.1.1-SNAPSHOT
     * project-name=KivaKit Metadata
     * project-group-id=com.telenav.kivakit.metadata
     * project-artifact-id=com.telenav.kivakit.metadata
     * </pre>
     *
     * @return The contents of the maven metadata file
     */
    public Map<String, String> projectProperties()
    {
        if (projectProperties == null)
        {
            projectProperties = properties(metadata(projectType, "/project.properties"));
        }

        return projectProperties;
    }

    /**
     * @return Metadata for a build of the given project at the current time
     */
    private static Metadata current(final Class<?> project)
    {
        return new Metadata(project, Type.CURRENT);
    }

    private static LocalDate currentBuildDate()
    {
        return LocalDateTime.now().atZone(ZoneId.of(ZoneOffset.UTC.getId())).toLocalDate();
    }

    /**
     * @return The build number for the given date in days since {@link #KIVAKIT_EPOCH_DAY}
     */
    private static int currentBuildNumber()
    {
        return (int) (currentBuildDate().toEpochDay() - KIVAKIT_EPOCH_DAY);
    }

    /**
     * @return The contents of the metadata resource at the given path
     */
    private static String metadata(final Class<?> project, final String path)
    {
        try
        {
            final var input = project.getResourceAsStream(path);
            return input == null ? null : new BufferedReader(new InputStreamReader(input))
                    .lines()
                    .collect(Collectors.joining("\n"))
                    .trim();
        }
        catch (final Exception cause)
        {
            throw new IllegalStateException("Unable to read: " + path, cause);
        }
    }

    /**
     * @return A properties map from the given text
     */
    private static Map<String, String> properties(final String text)
    {
        final var properties = new HashMap<String, String>();
        try
        {
            final var pattern = Pattern.compile("(?x) (?<key> [\\w-]+?) \\s* = \\s* (?<value> .*)");
            final var matcher = pattern.matcher(text);
            while (matcher.find())
            {
                properties.put(matcher.group("key"), matcher.group("value"));
            }
        }
        catch (final Exception ignored)
        {
        }
        return properties;
    }
}
