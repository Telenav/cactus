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

import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Thing that can replace the text content of a single DOM element specified by
 * an XSL query.
 *
 * @author Tim Boudreau
 */
public final class XMLTextContentReplacement extends AbstractXMLUpdater
{
    private final String xslQuery;
    private final String newValue;

    /**
     * Create a new replacement. If the new value is null, the found node will
     * be replaced with one which is self closing.
     *
     * @param in The file
     * @param xslQuery The query
     * @param newValue The new value or null
     */
    public XMLTextContentReplacement(XMLFile in, String xslQuery,
            String newValue)
    {
        super(in);
        this.xslQuery = notNull("xslQuery", xslQuery);
        this.newValue = newValue;
    }


    @Override
    public String toString()
    {
        return xslQuery + " -> " + newValue + " in " + in.path();
    }

    /**
     * Perform the replacement on the document.
     *
     * @return The document, if the change could be performed and the document
     * was really modified, or null if not.
     * @throws Exception If something goes wrong
     */
    public Document replace() throws Exception
    {
        return in.inContext(doc ->
        {
            return in.nodeQuery(xslQuery).map(node ->
            {
                boolean result = !Objects.equals(newValue, node.getTextContent()
                        .trim());
                if (result)
                {
                    if (newValue == null)
                    {
                        Node nue = doc.createElement(node.getNodeName());
                        node.getParentNode().replaceChild(nue, node);
                    }
                    else
                    {
                        node.setTextContent(newValue);
                    }
                }
                return doc;
            }).orElse(null);
        });
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 29 * hash + Objects.hashCode(this.in);
        hash = 29 * hash + Objects.hashCode(this.xslQuery);
        hash = 29 * hash + Objects.hashCode(this.newValue);
        return hash;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        else
            if (obj == null || obj.getClass() != XMLTextContentReplacement.class)
            {
                return false;
            }
        final XMLTextContentReplacement other = (XMLTextContentReplacement) obj;
        if (!Objects.equals(this.xslQuery, other.xslQuery))
            return false;
        if (!Objects.equals(this.newValue, other.newValue))
            return false;
        return Objects.equals(this.in, other.in);
    }
}
