package com.telenav.cactus.maven.model.internal;

import com.mastfrog.function.optional.ThrowingOptional;
import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingBiFunction;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingQuadFunction;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingSupplier;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.mastfrog.function.throwing.ThrowingTriFunction;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.ArtifactIdentifiers;
import com.telenav.cactus.maven.model.Dependency;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenCoordinates;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * We may be looking outside the build reactor, so avoid instantiating a full
 * Maven model for a pom file if we don't have to.
 *
 * This is internal api for the graph api and this module.
 *
 * @author Tim Boudreau
 */
public final class PomFile
{
    private static final Map<Pom, PomFile> CACHE = Collections.synchronizedMap(
            new WeakHashMap<>());
    private static final XPathFactory XPATH_FACTORY = XPathFactory
            .newDefaultInstance();
    private static final ThreadLocal<XPath> XPATH = ThreadLocal.withInitial(
            () -> XPATH_FACTORY.newXPath());
    private final ThreadLocal<Document> docContext = new ThreadLocal<>();
    public final Path path;

    public PomFile(Path path)
    {
        this.path = path;
    }

    public static PomFile of(Pom pom)
    {
        return CACHE.computeIfAbsent(pom, p -> new PomFile(p.pom));
    }

    public static void note(Pom pom, PomFile pomFile)
    {
        CACHE.computeIfAbsent(pom, p -> pomFile);
    }

    private static XPath xpath()
    {
        return XPATH.get();
    }

    public void visitProperties(BiConsumer<String, String> c) throws Exception
    {
        nodeQuery("/project/properties").ifPresent((Node nd) ->
        {
            NodeList list = nd.getChildNodes();
            for (int i = 0; i < list.getLength(); i++)
            {
                Node node = list.item(i);
                if (node instanceof Element)
                {
                    String name = node.getNodeName();
                    if (name != null && node.getTextContent() != null
                            && !node.getTextContent().isBlank())
                    {
                        c.accept(name, node.getTextContent().trim());
                    }
                }
            }
        });
    }

    public List<Dependency> dependencies(boolean depManagement) throws Exception
    {
        List<Dependency> result = new ArrayList<>();
        visitDependencies(depManagement, result::add);
        return result;
    }

    public void visitDependencies(boolean depManagement, Consumer<Dependency> dc)
            throws Exception
    {
        String q = depManagement
                   ? "/project/dependencyManagement/dependencies/dependency"
                   : "/project/dependencies/dependency";
        nodesQuery(q).ifPresent(depsNodes ->
        {
            for (int i = 0; i < depsNodes.getLength(); i++)
            {
                Node n = depsNodes.item(i);
                if (n instanceof Element && "dependency".equals(n.getNodeName()))
                {
                    Map<String, Node> kids = extractElements(n.getChildNodes());
                    Dependency dep = toDependency(kids);
                    if (dep != null)
                    {
                        dc.accept(dep);
                    }
                }
            }

        });
    }

    private static Map<String, Node> extractElements(NodeList depsNodes)
    {
        Map<String, Node> kids = new HashMap<>(6);
        for (int i = 0; i < depsNodes.getLength(); i++)
        {
            Node n = depsNodes.item(i);
            if (n instanceof Element)
            {
                kids.put(n.getNodeName(), n);
            }
        }
        return kids;
    }

    static Map<String, String> toStringMap(Map<String, Node> m)
    {
        Map<String, String> result = new TreeMap<>();
        m.forEach((k, v) ->
        {
            result.put(k, v.getTextContent() + "");
        });
        return result;
    }

    private Dependency toDependency(Map<String, Node> nodes)
    {
        ArtifactId aid = ArtifactId.of(nodes.get("artifactId"));
        GroupId gid = GroupId.of(nodes.get("groupId"));
        PomVersion ver = PomVersion.of(nodes.get("version"));
        if (aid == null || gid == null)
        {
            throw new IllegalStateException("Null aid or gid in " + toStringMap(
                    nodes));
        }
        MavenCoordinates coords = new MavenCoordinates(gid, aid, ver);
        boolean optional = "true".equals(nodeText("optional", nodes));
        String scope = nodeText("scope", nodes);
        String type = nodeText("type", nodes);
        Set<ArtifactIdentifiers> exclusions = exclusionSet(nodes.get("exclusions"));
        return new Dependency(coords, type, scope, optional, exclusions);
    }

    private static Set<ArtifactIdentifiers> exclusionSet(Node nd)
    {
        if (nd == null)
        {
            return Collections.emptySet();
        }
        Set<ArtifactIdentifiers> ids = new HashSet<>();
        NodeList kids = nd.getChildNodes();
        for (int i = 0; i < kids.getLength(); i++)
        {
            Node n = kids.item(i);
            if (n instanceof Element && "exclusion".equals(n.getNodeName()))
            {
                Map<String, Node> elems = extractElements(n.getChildNodes());
                Node aid = elems.get("artifactId");
                Node gid = elems.get("groupId");
                if (aid != null && gid != null)
                {
                    ids.add(new ArtifactIdentifiers(gid, aid));
                }
            }
        }
        return ids;
    }

    private static String nodeText(String key, Map<String, Node> m)
    {
        Node n = m.get(key);
        if (n != null)
        {
            return n.getTextContent().trim();
        }
        return null;
    }

    public <T> T inContext(ThrowingFunction<Document, T> supp) throws Exception
    {
        Document oldDoc = docContext.get();
        Document doc;
        if (oldDoc == null)
        {
            doc = document();
            docContext.set(doc);
        }
        else
        {
            doc = oldDoc;
        }
        try
        {
            return supp.apply(doc);
        }
        finally
        {
            if (oldDoc == null)
            {
                docContext.remove();
            }
        }
    }

    public <T> T inContext(ThrowingSupplier<T> supp) throws Exception
    {
        return inContext(doc -> supp.get());
    }

    public void inContextRun(ThrowingRunnable supp) throws Exception
    {
        this.inContext((ThrowingSupplier<Void>) (() ->
        {
            supp.run();
            return null;
        }));
    }

    public ThrowingOptional<ParentMavenCoordinates> parentCoordinates()
            throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {

        return nodeQueryGet("/project/parent/groupId",
                "/project/parent/artifactId", "/project/parent/version",
                (gid, aid, ver) ->
        {
            ThrowingOptional<Node> relPath = nodeQuery(
                    "/project/parent/relativePath");
            return new ParentMavenCoordinates(gid, aid, ver, relPath
                    .orElse(null));
        });
    }

    public ThrowingOptional<ParentMavenCoordinates> xparentCoordinates()
            throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findParentGroupId = xpath.compile(
                "/project/parent/groupId");
        XPathExpression findParentArtifactId = xpath.compile(
                "/project/parent/artifactId");
        XPathExpression findParentVersion = xpath.compile(
                "/project/parent/version");
        XPathExpression findParentRelativePath = xpath.compile(
                "/project/parent/relativePath");
        Node groupIdNode = (Node) findParentGroupId.evaluate(doc,
                XPathConstants.NODE);
        Node versionNode = (Node) findParentVersion.evaluate(doc,
                XPathConstants.NODE);
        Node artifactIdNode = (Node) findParentArtifactId.evaluate(doc,
                XPathConstants.NODE);
        Node relativePathNode = (Node) findParentRelativePath.evaluate(doc,
                XPathConstants.NODE);
        if (groupIdNode == null || artifactIdNode == null)
        {
            return ThrowingOptional.empty();
        }
        return ThrowingOptional.of(new ParentMavenCoordinates(groupIdNode,
                artifactIdNode, versionNode, relativePathNode));
    }

    public MavenCoordinates coordinates()
            throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        ThrowingOptional<Node> aid = nodeQuery("/project/artifactId");
        ThrowingOptional<Node> gid = nodeQuery("/project/groupId")
                .or(nodeQuery("/project/parent/groupId"));
        ThrowingOptional<Node> ver = nodeQuery("/project/version")
                .or(nodeQuery("/project/parent/version"));
        return new MavenCoordinates(gid.get(), aid.get(), ver.get());
    }

    public MavenCoordinates xcoordinates()
            throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findGroupId = xpath.compile("/project/groupId");
        XPathExpression findArtifactId = xpath.compile("/project/artifactId");
        XPathExpression findVersion = xpath.compile("/project/version");
        XPathExpression findParentGroupId = xpath.compile(
                "/project/parent/groupId");
        XPathExpression findParentVersion = xpath.compile(
                "/project/parent/version");

        Node groupIdNode = (Node) findGroupId.evaluate(doc, XPathConstants.NODE);
        if (groupIdNode == null)
        {
            groupIdNode = (Node) findParentGroupId.evaluate(doc,
                    XPathConstants.NODE);
        }
        Node versionNode = (Node) findVersion.evaluate(doc, XPathConstants.NODE);
        if (versionNode == null)
        {
            versionNode = (Node) findParentVersion.evaluate(doc,
                    XPathConstants.NODE);
        }

        Node artifactIdNode = (Node) findArtifactId.evaluate(doc,
                XPathConstants.NODE);
        return new MavenCoordinates(groupIdNode, artifactIdNode, versionNode);
    }

    public boolean isPom() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        return "pom".equals(packaging());
    }

    public Set<String> modules() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        Set<String> result = new LinkedHashSet<>();
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findModules = xpath.compile("/project/modules/module");
        NodeList moduleNodes = (NodeList) findModules.evaluate(doc,
                XPathConstants.NODESET);
        if (moduleNodes != null && moduleNodes.getLength() > 0)
        {
            for (int i = 0; i < moduleNodes.getLength(); i++)
            {
                Node n = moduleNodes.item(i);
                result.add(n.getTextContent());
            }
        }
        return result;
    }

    public String packaging() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findPackaging = xpath.compile("/project/packaging");
        Node packagingNode = (Node) findPackaging.evaluate(doc,
                XPathConstants.NODE);
        return packagingNode == null
               ? "jar"
               : packagingNode.getTextContent();
    }

    public ThrowingOptional<String> nodeText(String query)
            throws XPathExpressionException, ParserConfigurationException,
            SAXException, IOException
    {
        return nodeQuery(query).map(nd -> nd.getTextContent().trim());
    }

    public List<String> nodeTextList(String query)
            throws XPathExpressionException, ParserConfigurationException,
            SAXException, IOException
    {
        List<String> result = new ArrayList<>(32);
        nodesQuery(query).ifPresent(list ->
        {
            for (int i = 0; i < list.getLength(); i++)
            {
                Node no = list.item(i);
                String txt = no.getTextContent();
                if (txt != null)
                {
                    txt = txt.trim();
                    if (!txt.isEmpty())
                    {
                        result.add(txt);
                    }
                }
            }
        });
        return result;
    }

    public ThrowingOptional<Node> nodeQuery(String query)
            throws XPathExpressionException, ParserConfigurationException,
            SAXException, IOException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findPackaging = xpath.compile(query);
        Node result = (Node) findPackaging.evaluate(doc, XPathConstants.NODE);
        return ThrowingOptional.ofNullable(result);
    }

    public boolean nodeQuery(String query, ThrowingConsumer<Node> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd = nodeQuery(query);
        nd.ifPresent(c);
        return nd.isPresent();
    }

    public <T> ThrowingOptional<T> nodeQueryGet(String query,
            ThrowingFunction<Node, T> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd = nodeQuery(query);
        return nd.map(c);
    }

    public boolean nodeQuery(String query1, String query2,
            ThrowingBiConsumer<Node, Node> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);
        ThrowingOptional<Node> nd2 = nodeQuery(query2);
        nd1.ifPresent(n1 -> nd2.ifPresent(n2 -> c.accept(n1, n2)));
        return nd1.isPresent() && nd2.isPresent();
    }

    public boolean nodeQuery(String query1, String query2, String query3,
            ThrowingTriConsumer<Node, Node, Node> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);
        ThrowingOptional<Node> nd2 = nodeQuery(query2);
        ThrowingOptional<Node> nd3 = nodeQuery(query3);
        nd1.ifPresent(n1 -> nd2.ifPresent(n2 -> nd3.ifPresent(n3 -> c.accept(n1,
                n2, n3))));
        return nd1.isPresent() && nd2.isPresent() && nd3.isPresent();
    }

    public <T> ThrowingOptional<T> nodeQueryGet(String query1, String query2,
            ThrowingBiFunction<Node, Node, T> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);

        return nd1.flatMapThrowing(n1 ->
        {
            ThrowingOptional<Node> nd2 = nodeQuery(query2);
            return nd2.map(n2 ->
            {
                return c.apply(n1, n2);
            });
        });
    }

    public <T> ThrowingOptional<T> nodeQueryGet(String query1, String query2,
            String query3,
            ThrowingTriFunction<Node, Node, Node, T> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);

        return nd1.<T>flatMapThrowing(n1 ->
        {
            ThrowingOptional<Node> nd2 = nodeQuery(query2);
            return nd2.<T>flatMapThrowing(n2 ->
            {
                ThrowingOptional<Node> nd3 = nodeQuery(query3);
                return nd3.<T>map(n3 -> c.apply(n1, n2, n3));
            });
        });
    }

    public <T> ThrowingOptional<T> nodeQueryGet(String query1, String query2,
            String query3, String query4,
            ThrowingQuadFunction<Node, Node, Node, Node, T> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);

        return nd1.flatMapThrowing(n1 ->
        {
            ThrowingOptional<Node> nd2 = nodeQuery(query2);
            return nd2.flatMapThrowing(n2 ->
            {
                ThrowingOptional<Node> nd3 = nodeQuery(query3);

                return nd3.flatMapThrowing(n3 ->
                {
                    ThrowingOptional<Node> nd4 = nodeQuery(query4);
                    return nd4.map(n4 -> c.apply(n1, n2, n3, n4));
                });
            });
        });
    }

    private ThrowingOptional<NodeList> nodesQuery(String query)
            throws XPathExpressionException, ParserConfigurationException,
            SAXException, IOException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findPackaging = xpath.compile(query);
        NodeList result = (NodeList) findPackaging.evaluate(doc,
                XPathConstants.NODESET);
        if (result == null || result.getLength() == 0)
        {
            return ThrowingOptional.empty();
        }
        return ThrowingOptional.of(result);
    }

    private Document document() throws ParserConfigurationException,
            SAXException, IOException
    {
        Document result = docContext.get();
        if (result == null)
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory
                    .newDefaultInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            result = db.parse(path.toFile());
        }
        return result;
    }
}
