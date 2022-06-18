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
package com.telenav.cactus.maven.sourceanalysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 *
 * @author Tim Boudreau
 */
final class CodeflowersIndexGenerator
{

    private final Path dir;

    public CodeflowersIndexGenerator(Path dir)
    {
        this.dir = dir;
    }

    /**
     * Generate an index based on .wc and .json files in the directory with the
     * same name.
     *
     * @param title The name of the project
     * @throws IOException
     */
    public void generate(String title) throws IOException
    {
        generate(title, deriveOptionsFromFiles());
    }

    public void generate(String title, Set<String> ids) throws IOException
    {
        String result = template().replaceAll("__PROJECT__", title).replaceAll(
                "__OPTIONS__", options(ids));
        Files.write(dir.resolve("index.html"), result.getBytes(UTF_8), WRITE,
                TRUNCATE_EXISTING, CREATE);
        unzipAssets();
    }

    private void unzipAssets() throws IOException
    {
        try ( InputStream in = CodeflowersIndexGenerator.class
                .getResourceAsStream("cf.zip"))
        {
            if (in == null)
            {
                throw new IOException("cf.zip not adjacent to "
                        + CodeflowersIndexGenerator.class + " on classpath");
            }
            try ( ZipInputStream zip = new ZipInputStream(in))
            {
                ZipEntry en;
                while ((en = zip.getNextEntry()) != null)
                {
                    if (en.isDirectory())
                    {
                        Path dest = dir.resolve(en.getName());
                        if (!Files.exists(dest))
                        {
                            Files.createDirectories(dest);
                        }
                    }
                    else
                    {
                        Path dest = dir.resolve(en.getName());
                        if (!Files.exists(dest.getParent()))
                        {
                            Files.createDirectories(dest.getParent());
                        }
                        Files.copy(zip, dest,
                                StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static String template() throws IOException
    {
        try ( InputStream in = CodeflowersIndexGenerator.class
                .getResourceAsStream("index-template.html"))
        {
            if (in == null)
            {
                throw new IOException("index-template.html not adjacent to "
                        + CodeflowersIndexGenerator.class + " on classpath");
            }
            return new String(in.readAllBytes(), UTF_8);
        }
    }

    private String options(Set<String> ids)
    {
        StringBuilder sb = new StringBuilder();
        for (String id : ids)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            String friendlyName = friendlyName(id);
            sb.append("<option value='data/").append(id).append(".json'>")
                    .append(friendlyName).append("</option>");
        }
        return sb.toString();
    }

    private static String friendlyName(String name)
    {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("-"))
        {
            char[] c = part.toCharArray();
            c[0] = Character.toUpperCase(c[0]);
            if (sb.length() > 0)
            {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private Set<String> deriveOptionsFromFiles() throws IOException
    {
        Set<String> wcFiles = new HashSet<>();
        Set<String> jsonFiles = new HashSet<>();
        try ( Stream<Path> allFiles = Files.list(dir))
        {
            allFiles.forEach(file ->
            {
                if (Files.isDirectory(file))
                {
                    return;
                }
                if (isJsonFile(file))
                {
                    jsonFiles.add(rawFileName(file));
                }
                else
                    if (isWc(file))
                    {
                        wcFiles.add(rawFileName(file));
                    }
            });
        }
        Set<String> result = new TreeSet<>(wcFiles);
        result.retainAll(jsonFiles);
        return result;
    }

    private static String rawFileName(Path file)
    {
        String fn = file.getFileName().toString();
        int ix = fn.lastIndexOf('.');
        if (ix < 0)
        {
            return fn;
        }
        return fn.substring(0, ix);
    }

    private static boolean isJsonFile(Path path)
    {
        return path.getFileName().toString().endsWith(".json");
    }

    private static boolean isWc(Path path)
    {
        return path.getFileName().toString().endsWith(".json");
    }

}
