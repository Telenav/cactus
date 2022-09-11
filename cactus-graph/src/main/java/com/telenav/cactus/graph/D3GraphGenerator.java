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
package com.telenav.cactus.graph;

import com.mastfrog.function.throwing.io.IOBiConsumer;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.graph.algorithm.Score;
import com.mastfrog.util.fileformat.SimpleJSON;
import com.mastfrog.util.streams.Streams;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.MavenIdentified;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 *
 * @author tim
 */
public final class D3GraphGenerator
{

    private final ObjectGraph<MavenCoordinates> graph;
    private static final DecimalFormat FMT = new DecimalFormat(
            "#####.################");

    private double alpha = 0.400;
    private double alphaDecay = 0.00002;
    private double velocityDecay = 0.725;
    private int chargeForceStrength = -853;
    private int chargeForceDistanceMax = 3750;
    private double chargeForceTheta = 4D;
    private double collideForceRadiusFactor = 120;
    private int collideForceStrength = 550;
    private int collideForceIterations = 150;
    private Function<MavenIdentified, String> familyFinder = D3GraphGenerator::family;

    public D3GraphGenerator(ObjectGraph<MavenCoordinates> graph)
    {
        this.graph = graph;
    }

    public D3GraphGenerator withFamiliesFor(String prefix)
    {
        return withCategorizer(artifact ->
        {
            return artifact.groupId().textStartsWith(prefix)
                   ? family(artifact)
                   : artifact.groupId().text();
        });
    }

    public D3GraphGenerator withCategorizer(
            Function<MavenIdentified, String> func)
    {
        familyFinder = func;
        return this;
    }

    public D3GraphGenerator withAlpha(double alpha)
    {
        this.alpha = alpha;
        return this;
    }

    public D3GraphGenerator withAlphaDecay(double alphaDecay)
    {
        this.alphaDecay = alphaDecay;
        return this;
    }

    public D3GraphGenerator withVelocityDecay(double velocityDecay)
    {
        this.velocityDecay = velocityDecay;
        return this;
    }

    public D3GraphGenerator withChargeForceStrength(int chargeForceStrength)
    {
        this.chargeForceStrength = chargeForceStrength;
        return this;
    }

    public D3GraphGenerator withChargeForceDistanceMax(
            int chargeForceDistanceMax)
    {
        this.chargeForceDistanceMax = chargeForceDistanceMax;
        return this;
    }

    public D3GraphGenerator withChargeForceTheta(double chargeForceTheta)
    {
        this.chargeForceTheta = chargeForceTheta;
        return this;
    }

    public D3GraphGenerator withCollideForceRadiusFactor(
            double collideForceRadiusFactor)
    {
        this.collideForceRadiusFactor = collideForceRadiusFactor;
        return this;
    }

    public D3GraphGenerator withCollideForceIterations(
            int collideForceIterations)
    {
        this.collideForceIterations = collideForceIterations;
        return this;
    }

    public D3GraphGenerator withCollideForceStrength(int collideForceStrength)
    {
        this.collideForceStrength = collideForceStrength;
        return this;
    }

    private String applyProperties(String to)
    {
        return to.replaceAll("__ALPHA__", FMT.format(alpha))
                .replaceAll("__ALPHA_DECAY__", FMT.format(alphaDecay))
                .replaceAll("__VELOCITY_DECAY__", FMT.format(velocityDecay))
                .replaceAll("__CHARGE_FORCE_STRENGTH__", Integer.toString(
                        chargeForceStrength))
                .replaceAll("__CHARGE_FORCE_DISTANCE_MAX__", FMT.format(
                        chargeForceDistanceMax))
                .replaceAll("__CHARGE_FORCE_THETA__", FMT.format(
                        chargeForceTheta))
                .replaceAll("__COLLIDE_FORCE_RADIUS_FACTOR__", FMT.format(
                        collideForceRadiusFactor))
                .replaceAll("__COLLIDE_FORCE_STRENGTH__", Integer.toString(
                        collideForceStrength))
                .replaceAll("__COLLIDE_FORCE_ITERATIONS__", Integer.toString(
                        collideForceIterations));
    }

    private Object idOf(ObjectGraph<MavenCoordinates> graph,
            MavenCoordinates coords)
    {
        return graph.toNodeId(coords);
    }

    private String nameOf(MavenIdentified coords)
    {
        return coords.artifactId().toString();
    }

    public ObjectGraph<MavenCoordinates> generate(Path into) throws IOException
    {
        notNull("into", into);
        scoreMap(graph.pageRank(), (scores, groups) ->
        {
            List<Map<String, Object>> nodes = new ArrayList<>(graph.size());
            List<Map<String, Object>> links = new ArrayList<>(graph.size());
            for (int i = 0; i < graph.size(); i++)
            {
                MavenCoordinates coords = graph.toNode(i);
                Map<String, Object> map = new TreeMap<>();
                map.put("id", idOf(graph, coords));
                map.put("name", nameOf(coords));
                map.put("rank", scores.get(coords));
                map.put("group", groups.get(coords));
                nodes.add(map);
                graph.children(coords).forEach(kid ->
                {
                    Map<String, Object> link = new HashMap<>();
                    link.put("source", map.get("id"));
                    link.put("target", idOf(graph, kid));
                    link.put("value", 1); // What is this?
                    links.add(link);
                });
            }
            Map<String, Object> all = new HashMap<>();
            all.put("nodes", nodes);
            all.put("links", links);
            String output = SimpleJSON.stringify(all, SimpleJSON.Style.MINIFIED);
            if (into.getParent() != null && !Files.exists(into.getParent()))
            {
                Files.createDirectories(into.getParent());
            }

            Files.writeString(into, output, UTF_8, WRITE, TRUNCATE_EXISTING,
                    CREATE);
            System.out.println("Write " + output);
            Path dir = into.getParent();
            try ( InputStream in = D3GraphGenerator.class.getResourceAsStream(
                    "index.html"))
            {
                String data = applyProperties(Streams.readString(in, UTF_8)
                        .replaceAll(
                                "__FILE__", into.getFileName().toString()));
                Path index = dir.resolve("index.html");
                Files.writeString(index, data, UTF_8, WRITE, TRUNCATE_EXISTING,
                        CREATE);
                System.out.println("Wrote " + index);
            }
        });
        return graph;
    }

    private void scoreMap(List<Score<MavenCoordinates>> scores,
            IOBiConsumer<Map<MavenCoordinates, Double>, Map<MavenCoordinates, Integer>> c)
            throws IOException
    {
        Map<MavenCoordinates, Double> result = new HashMap<>();
        Map<MavenCoordinates, Integer> groups = new HashMap<>();
        Set<String> allFamilies = new HashSet<>();
        List<String> families = new ArrayList<>();
        double min = Integer.MAX_VALUE;
        double max = Integer.MIN_VALUE;
        for (Score<MavenCoordinates> sc : scores)
        {
            min = Math.min(sc.score(), min);
            max = Math.max(sc.score(), max);
        }
        // Normalize the scores to 0..1
        double range = max - min;
        double factor = range == 0
                        ? 1
                        : 1 / range;

        System.out.println(
                "MIN " + min + " max " + max + " range " + range + " factor " + factor);
        double mx = max;
        double mn = min;
        scores.forEach(score ->
        {
            double normScore = 0.0 + (1D - ((range - (score.score() - mn)) * factor));
            if (!Double.isFinite(normScore))
            {
                normScore = 1;
            }
            System.out.println(score.node() + " " + normScore);
            result.put(score.node(), normScore);
            String fam = familyFinder.apply(score.node());
            int ix;
            if (allFamilies.add(fam))
            {
                ix = families.size();
                families.add(fam);
            }
            else
            {
                ix = families.indexOf(fam);
            }
            groups.put(score.node(), ix);
        });
        c.accept(result, groups);
    }

    private static String family(MavenIdentified artifact)
    {
        String gid = artifact.groupId().text();
        int ix = gid.lastIndexOf('.');
        return gid.substring(ix + 1);
    }

    private static String family(GroupId groupId)
    {
        String gid = groupId.text();
        int ix = gid.lastIndexOf('.');
        return gid.substring(ix + 1);
    }
}
