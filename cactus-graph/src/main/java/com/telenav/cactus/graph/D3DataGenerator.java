package com.telenav.cactus.graph;

import com.mastfrog.function.throwing.io.IOBiConsumer;
import com.mastfrog.graph.ObjectGraph;
import com.mastfrog.graph.algorithm.Score;
import com.mastfrog.util.fileformat.SimpleJSON;
import com.mastfrog.util.streams.Streams;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 *
 * @author tim
 */
public final class D3DataGenerator
{

    private final ObjectGraph<MavenCoordinates> graph;
    private static final DecimalFormat FMT = new DecimalFormat(
            "#####.################");

    public D3DataGenerator(ObjectGraph<MavenCoordinates> graph)
    {
        this.graph = graph;
    }

    private double alpha = 0.9;
    private double alphaDecay = 0.00001;
    private double velocityDecay = 0.725;
    private int chargeForceStrength = -203;
    private int chargeForceDistanceMax = 8200;
    private double chargeForceTheta = 1.125D;
    private double collideForceRadiusFactor = 5.5;
    private int collideForceStrength = 122;
    private int collideForceIterations = 122;

    public D3DataGenerator setAlpha(double alpha)
    {
        this.alpha = alpha;
        return this;
    }

    public D3DataGenerator setAlphaDecay(double alphaDecay)
    {
        this.alphaDecay = alphaDecay;
        return this;
    }

    public D3DataGenerator setVelocityDecay(double velocityDecay)
    {
        this.velocityDecay = velocityDecay;
        return this;
    }

    public D3DataGenerator setChargeForceStrength(int chargeForceStrength)
    {
        this.chargeForceStrength = chargeForceStrength;
        return this;
    }

    public D3DataGenerator setChargeForceDistanceMax(int chargeForceDistanceMax)
    {
        this.chargeForceDistanceMax = chargeForceDistanceMax;
        return this;
    }

    public D3DataGenerator setChargeForceTheta(double chargeForceTheta)
    {
        this.chargeForceTheta = chargeForceTheta;
        return this;
    }

    public D3DataGenerator setCollideForceRadiusFactor(
            double collideForceRadiusFactor)
    {
        this.collideForceRadiusFactor = collideForceRadiusFactor;
        return this;
    }

    public D3DataGenerator setCollideForceIterations(int collideForceIterations)
    {
        this.collideForceIterations = collideForceIterations;
        return this;
    }

    private String applyProperties(String to)
    {
        return to.replaceAll("__ALPHA__", FMT.format(alpha))
                .replaceAll("__ALPHA_DECAY__", FMT.format(alphaDecay))
                .replaceAll("__VELOCITY_DECAY__", FMT.format(velocityDecay))
                .replaceAll("__CHARGE_FORCE_STRENGTH__", FMT.format(
                        chargeForceStrength))
                .replaceAll("__CHARGE_FORCE_DISTANCE_MAX__", FMT.format(
                        chargeForceDistanceMax))
                .replaceAll("__CHARGE_FORCE_THETA__", FMT.format(
                        chargeForceTheta))
                .replaceAll("__COLLIDE_FORCE_RADIUS_FACTOR__", FMT.format(
                        collideForceRadiusFactor))
                .replaceAll("__COLLIDE_FORCE_ITERATIONS__", FMT.format(
                        collideForceIterations));
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
                map.put("id", family(coords.groupId()) + ":" + coords
                        .artifactId().text());
                map.put("pagerank", scores.get(coords));
                map.put("group", groups.get(coords));
                nodes.add(map);
                graph.children(coords).forEach(kid ->
                {
                    Map<String, Object> link = new HashMap<>();
                    link.put("source", map.get("id"));
                    link.put("target", family(kid.groupId()) + ":" + kid
                            .artifactId());
                    link.put("value", 2); // What is this?
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
            try ( InputStream in = D3DataGenerator.class.getResourceAsStream(
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

    private static void scoreMap(List<Score<MavenCoordinates>> scores,
            IOBiConsumer<Map<MavenCoordinates, Double>, Map<MavenCoordinates, Integer>> c)
            throws IOException
    {
        Map<MavenCoordinates, Double> result = new HashMap<>();
        Map<MavenCoordinates, Integer> groups = new HashMap<>();
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
            double normScore = 0.1 + (1D - ((range - (score.score() - mn)) * factor));
            if (!Double.isFinite(normScore)) {
                normScore = 1;
            }
            System.out.println(score.node() + " " + normScore);
            result.put(score.node(), normScore);
            String fam = family(score.node().groupId());
            int ix = families.indexOf(fam);
            if (ix < 0)
            {
                ix = families.size();
                families.add(fam);
            }
            groups.put(score.node(), ix);
        });
        c.accept(result, groups);
    }

    private static String family(GroupId groupId)
    {
        String gid = groupId.text();
        int ix = gid.lastIndexOf('.');
        return gid.substring(ix + 1);
    }
}
