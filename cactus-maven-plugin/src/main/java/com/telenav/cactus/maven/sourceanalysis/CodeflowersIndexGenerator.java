package com.telenav.cactus.maven.sourceanalysis;

import java.io.IOException;
import java.io.InputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Tim Boudreau
 */
public class CodeflowersIndexGenerator
{

    private final Path dir;

    public CodeflowersIndexGenerator(Path dir)
    {
        this.dir = dir;
    }

    public void generate(String title, Set<String> ids) throws IOException
    {
        String result = template().replaceAll("__PROJECT__", title).replaceAll("__OPTIONS__", options(ids));
        Files.write(dir.resolve("index.html"), result.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING, CREATE);
        unzipAssets();
    }

    private void unzipAssets() throws IOException
    {
        try ( InputStream in = CodeflowersIndexGenerator.class.getResourceAsStream("cf.zip"))
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
                    } else
                    {
                        Path dest = dir.resolve(en.getName());
                        if (!Files.exists(dest.getParent()))
                        {
                            Files.createDirectories(dest.getParent());
                        }
                        Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static String template() throws IOException
    {
        try ( InputStream in = CodeflowersIndexGenerator.class.getResourceAsStream("index-template.html"))
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
}
