package com.telenav.cactus.maven.model.internal;

import com.telenav.cactus.maven.xml.XMLFile;
import com.mastfrog.function.optional.ThrowingOptional;
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
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
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
public final class PomFile extends XMLFile
{
    private static final Map<Pom, PomFile> CACHE = Collections.synchronizedMap(
            new WeakHashMap<>());


    public PomFile(Path path)
    {
        super(path);
    }

    public static PomFile of(Pom pom)
    {
        return CACHE.computeIfAbsent(pom, p -> new PomFile(p.pom));
    }

    public static void note(Pom pom, PomFile pomFile)
    {
        CACHE.computeIfAbsent(pom, p -> pomFile);
    }

    public boolean isPom() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        return "pom".equals(packaging());
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
}
