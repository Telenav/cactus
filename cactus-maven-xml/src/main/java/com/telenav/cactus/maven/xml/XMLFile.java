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
package com.telenav.cactus.maven.xml;

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
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Wraps a Path in logic which can read an XML document and perform queries on
 * it.
 *
 * @author Tim Boudreau
 */
public class XMLFile
{
    private static final XPathFactory XPATH_FACTORY = XPathFactory
            .newInstance();
    protected static final ThreadLocal<XPath> XPATH
            = ThreadLocal.withInitial(() -> XPATH_FACTORY.newXPath());
    protected final ThreadLocal<Document> docContext = new ThreadLocal<>();
    private final Path path;

    public XMLFile(Path path)
    {
        this.path = notNull("path", path);
    }

    /**
     * Get the document. If inside the closure of one of the inContext()
     * methods, the returned document will be held until exit of that context,
     * so changes can be aggregated.
     *
     * @return A document
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    public Document document() throws ParserConfigurationException,
            SAXException, IOException
    {
        Document result = docContext.get();
        if (result == null)
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            result = db.parse(path.toFile());
        }
        return result;
    }

    protected static Map<String, String> toStringMap(Map<String, Node> m)
    {
        Map<String, String> result = new TreeMap<>();
        m.forEach((k, v) ->
        {
            result.put(k, v.getTextContent() + "");
        });
        return result;
    }

    protected static String nodeText(String key, Map<String, Node> m)
    {
        Node n = m.get(key);
        if (n != null)
        {
            return n.getTextContent().trim();
        }
        return null;
    }

    public final <T> T inContext(ThrowingFunction<Document, T> supp) throws Exception
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

    public final <T> T inContext(ThrowingSupplier<T> supp) throws Exception
    {
        return inContext(doc -> supp.get());
    }

    public final void inContextRun(ThrowingRunnable supp) throws Exception
    {
        this.inContext((ThrowingSupplier<Void>) (() ->
        {
            supp.run();
            return null;
        }));
    }

    public final ThrowingOptional<String> nodeText(String query) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        return nodeQuery(query)
                .map(nd -> nd.getTextContent().trim());
    }

    public final List<String> nodeTextList(String query) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        List<String> result = new ArrayList<>(32);
        nodesQuery(query)
                .ifPresent(list ->
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

    public final ThrowingOptional<Node> nodeQuery(String query) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        XPath xpath = xpath();
        Document doc = document();
        XPathExpression findPackaging = xpath.compile(query);
        Node result = (Node) findPackaging.evaluate(doc, XPathConstants.NODE);
        return ThrowingOptional.ofNullable(result);
    }

    public final boolean nodeQuery(String query, ThrowingConsumer<Node> c)
            throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd = nodeQuery(query);
        nd.ifPresent(c);
        return nd.isPresent();
    }

    public final <T> ThrowingOptional<T> nodeQueryGet(String query,
            ThrowingFunction<Node, T> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd = nodeQuery(query);
        return nd.map(c);
    }

    public final boolean nodeQuery(String query1, String query2,
            ThrowingBiConsumer<Node, Node> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);
        ThrowingOptional<Node> nd2 = nodeQuery(query2);
        nd1.ifPresent(n1 -> nd2.ifPresent(n2 -> c.accept(n1, n2)));
        return nd1.isPresent() && nd2.isPresent();
    }

    public final boolean nodeQuery(String query1, String query2, String query3,
            ThrowingTriConsumer<Node, Node, Node> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
    {
        ThrowingOptional<Node> nd1 = nodeQuery(query1);
        ThrowingOptional<Node> nd2 = nodeQuery(query2);
        ThrowingOptional<Node> nd3 = nodeQuery(query3);
        nd1.ifPresent(n1 -> nd2.ifPresent(n2 -> nd3.ifPresent(n3 -> c.accept(n1,
                n2, n3))));
        return nd1.isPresent() && nd2.isPresent() && nd3.isPresent();
    }

    public final <T> ThrowingOptional<T> nodeQueryGet(String query1,
            String query2,
            ThrowingBiFunction<Node, Node, T> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
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

    public final <T> ThrowingOptional<T> nodeQueryGet(String query1,
            String query2,
            String query3, ThrowingTriFunction<Node, Node, Node, T> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
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

    public final <T> ThrowingOptional<T> nodeQueryGet(String query1,
            String query2,
            String query3, String query4,
            ThrowingQuadFunction<Node, Node, Node, Node, T> c) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
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

    public final ThrowingOptional<NodeList> nodesQuery(String query) throws XPathExpressionException, ParserConfigurationException, SAXException, IOException
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

    protected static XPath xpath()
    {
        return XPATH.get();
    }

    /**
     * Get the file represented by this XMLFile.
     *
     * @return the path
     */
    public final Path path()
    {
        return path;
    }
}
