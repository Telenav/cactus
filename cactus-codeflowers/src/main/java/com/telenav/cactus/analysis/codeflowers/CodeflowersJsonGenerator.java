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
package com.telenav.cactus.analysis.codeflowers;

import com.telenav.cactus.analysis.ProjectScanConsumer;
import com.mastfrog.function.state.Int;
import com.telenav.cactus.maven.model.Pom;
import java.io.IOException;
import java.io.OutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Tim Boudreau
 */
public final class CodeflowersJsonGenerator implements ProjectScanConsumer
{

    private final String title;

    private final Path outputDir;
    private final boolean whitespace;
    private final Set<String> artifactIds = ConcurrentHashMap.newKeySet();
    private final boolean pretend;

    public CodeflowersJsonGenerator(String title, Path outputDir,
            boolean whitespace, boolean pretend)
    {
        this.title = title;
        this.outputDir = outputDir;
        this.whitespace = whitespace;
        this.pretend = pretend;
    }

    @Override
    public void onDone() throws IOException
    {
        new CodeflowersIndexGenerator(outputDir.getParent()).generate(title,
                new TreeSet<>(artifactIds));
    }

    @Override
    public void onProjectScanned(Pom pom, Map<Path, Integer> scores) throws IOException
    {
        artifactIds.add(pom.artifactId().text());
        HFolder root = new HFolder();
        scores.forEach((path, score) ->
        {
            if (path.getParent() == null)
            {
                root.addFile(path.getFileName().toString(), score);
                return;
            }
            root.findChildFolder(path.getParent(), 0).addFile(path.getFileName()
                    .toString(), score);
        });
        String fnBase = pom.artifactId().text();
        if (!Files.exists(outputDir))
        {
            synchronized (CodeflowersJsonGenerator.class)
            {
                if (!Files.exists(outputDir))
                {
                    try
                    {
                        Files.createDirectories(outputDir);
                    }
                    catch (IOException ioe)
                    {
                        // this can race - we are being called on multiple threads here
                    }
                }
            }
        }
        Path jsonFile = outputDir.resolve(fnBase + ".json");
        Path wcFile = outputDir.resolve(fnBase + ".wc");
        if (!pretend)
        {
            try ( OutputStream jsonOut = Files.newOutputStream(jsonFile, WRITE,
                    TRUNCATE_EXISTING, CREATE))
            {
                jsonOut.write(root.jsonify(0, new SB(whitespace)).toString()
                        .getBytes(UTF_8));
            }
        }
        StringBuilder wc = new StringBuilder();
        Int total = Int.create();
        scores.forEach((path, score) ->
        {
            if (wc.length() > 0)
            {
                wc.append('\n');
            }
            total.increment(score);
            String txt = Integer.toString(score);
            for (int i = 0; i < 7 - txt.length(); i++)
            {
                wc.append(' ');
            }
            wc.append(txt).append(' ').append(path);
        });
        wc.append('\n');
        String tot = total.toString();
        for (int i = 0; i < 7 - tot.length(); i++)
        {
            wc.append(' ');
        }
        wc.append(tot).append(" total\n");
        if (!pretend)
        {
            try ( OutputStream wcOut = Files.newOutputStream(wcFile, WRITE,
                    TRUNCATE_EXISTING, CREATE))
            {
                wcOut.write(wc.toString().getBytes(UTF_8));
            }
        }
    }

    private static final class HFolder implements Comparable<HFolder>
    {

        private final Map<String, HFolder> childFolders = new TreeMap<>();
        private final String name;
        private final Map<String, Integer> childFileScores = new TreeMap<>();
        private static final String NAME_PREFIX = "\"name\" : ";
        private static final String CHILDREN_PREFIX = "\"children\" : [";
        private static final String SIZE_PREFIX = "\"size\" : ";

        @SuppressWarnings("LeakingThisInConstructor")
        HFolder(String name)
        {
            this.name = name;
        }

        HFolder()
        {
            this("");
        }

        @Override
        public String toString()
        {
            return jsonify(0, new SB(false)).toString();
        }

        SB jsonify(int depth, SB sb)
        {
            // Pulling in a json lib would be a potent source of conflicts
            // with other plugins, and the JSON we need is simple enough to
            // just generate it manually.
            if (sb.length() > 0)
            {
                sb.newline();
            }
            sb.spaces(depth).append('{').newline();
            if (!name.isEmpty())
            {
                sb.spaces(depth).append(NAME_PREFIX).quote(name);
            }
            if (!childFolders.isEmpty() || !childFileScores.isEmpty())
            {
                if (!name.isEmpty())
                {
                    sb.append(',').newline();
                }
                sb.spaces(name.isEmpty()
                          ? depth + 1
                          : depth).append(CHILDREN_PREFIX);
                for (Iterator<Map.Entry<String, Integer>> it
                        = childFileScores.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<String, Integer> en = it.next();
                    String child = en.getKey();
                    Integer score = en.getValue();

                    sb.newline().spaces(depth + 2).append("{");
                    sb.newline().spaces(depth + 3)
                            .append(NAME_PREFIX).quote(child).append(',');
                    sb.newline().spaces(depth + 3)
                            .append(SIZE_PREFIX).append(score);
                    sb.newline().spaces(depth + 2).append("}");
                    if (it.hasNext() || !childFolders.isEmpty())
                    {
                        sb.append(",");
                    }
                }
                for (Iterator<Map.Entry<String, HFolder>> it
                        = childFolders.entrySet().iterator(); it.hasNext();)
                {
                    Map.Entry<String, HFolder> en = it.next();
                    en.getValue().jsonify(depth + 1, sb);
                    if (it.hasNext())
                    {
                        sb.append(',');
                    }
                }
                sb.append(']');
            }
            sb.newline().spaces(depth).append('}');
            if (name.isEmpty())
            {
                sb.sb.append('\n');
            }
            return sb;
        }

        void addFile(String filename, int score)
        {
            childFileScores.put(filename, score);
        }

        HFolder findChildFolder(Path folderPath, int nameIndex)
        {
            if (nameIndex >= folderPath.getNameCount())
            {
                return this;
            }
            String nm = folderPath.getName(nameIndex).toString();
            HFolder result = childFolders.get(nm);
            if (result == null)
            {
                result = new HFolder(nm);
                childFolders.put(nm, result);
            }
            return result.findChildFolder(folderPath, nameIndex + 1);
        }

        @Override
        public int compareTo(HFolder o)
        {
            return name.compareTo(o.name);
        }
    }

    static class SB
    {

        private final StringBuilder sb = new StringBuilder();
        private final boolean whitespace;

        SB(boolean whitespace)
        {
            this.whitespace = whitespace;
        }

        SB()
        {
            this(true);
        }

        int length()
        {
            return sb.length();
        }

        public SB newline()
        {
            if (whitespace)
            {
                sb.append('\n');
            }
            return this;
        }

        public SB append(char c)
        {
            if (!whitespace && c == '\n')
            {
                return this;
            }
            sb.append(c);
            return this;
        }

        public SB append(String s)
        {
            if (!whitespace)
            {
                s = s.replaceAll("\n", "");
                s = s.replaceAll(" : ", ":");
            }
            sb.append(s);
            return this;
        }

        public SB append(Object o)
        {
            sb.append(o);
            return this;
        }

        public SB spaces(int count)
        {
            if (!whitespace)
            {
                return this;
            }
            char[] c = new char[count * 2];
            Arrays.fill(c, ' ');
            sb.append(c);
            return this;
        }

        public SB quote(String what)
        {
            sb.append('"').append(what.replaceAll("\"", "\\\"")).append('"');
            return this;
        }

        public String toString()
        {
            return sb.toString();
        }
    }
}
