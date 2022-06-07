package com.telenav.cactus.maven.model;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/**
 * We may be looking outside the build reactor, so avoid instantiating a full Maven model for a pom file if we don't
 * have to.
 *
 * @author Tim Boudreau
 */
public final class PomFile
{
    public final Path path;

    public PomFile(Path path)
    {
        this.path = path;
    }

    public MavenCoordinates coordinates() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        XPathFactory xp = XPathFactory.newDefaultInstance();
        XPath xpath = xp.newXPath();
        Document doc = document();
        XPathExpression findGroupId = xpath.compile("/project/groupId");
        XPathExpression findArtifactId = xpath.compile("/project/artifactId");
        XPathExpression findVersion = xpath.compile("/project/version");
        XPathExpression findParentGroupId = xpath.compile("/project/parent/groupId");
        XPathExpression findParentVersion = xpath.compile("/project/parent/version");

        Node groupIdNode = (Node) findGroupId.evaluate(doc, XPathConstants.NODE);
        if (groupIdNode == null)
        {
            groupIdNode = (Node) findParentGroupId.evaluate(doc, XPathConstants.NODE);
        }
        Node versionNode = (Node) findVersion.evaluate(doc, XPathConstants.NODE);
        if (versionNode == null)
        {
            versionNode = (Node) findParentVersion.evaluate(doc, XPathConstants.NODE);
        }

        Node artifactIdNode = (Node) findArtifactId.evaluate(doc, XPathConstants.NODE);
        return new MavenCoordinates(groupIdNode, artifactIdNode, versionNode);
    }

    public boolean isPom() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        return "pom".equals(packaging());
    }

    public Set<String> modules() throws ParserConfigurationException, SAXException, IOException, XPathExpressionException
    {
        Set<String> result = new HashSet<>();
        XPathFactory xp = XPathFactory.newDefaultInstance();
        XPath xpath = xp.newXPath();
        Document doc = document();
        XPathExpression findModules = xpath.compile("/project/modules/module");
        NodeList moduleNodes = (NodeList) findModules.evaluate(doc, XPathConstants.NODESET);
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
        XPathFactory xp = XPathFactory.newDefaultInstance();
        XPath xpath = xp.newXPath();
        Document doc = document();
        XPathExpression findPackaging = xpath.compile("/project/packaging");
        Node packagingNode = (Node) findPackaging.evaluate(doc, XPathConstants.NODE);
        return packagingNode == null ? "jar" : packagingNode.getTextContent();
    }

    private Document document() throws ParserConfigurationException, SAXException, IOException
    {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(path.toFile());
    }
}
