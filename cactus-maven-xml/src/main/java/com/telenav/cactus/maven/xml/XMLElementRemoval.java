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
import java.util.Objects;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * Deletes the result of an xpath query if it is matched.
 *
 * @author Tim Boudreau
 */
public final class XMLElementRemoval extends AbstractXMLUpdater
{
    private final String query;

    public XMLElementRemoval(XMLFile file, String query)
    {
        super(file);
        this.query = query;
    }

    @Override
    public Document replace() throws Exception
    {
        return in.inContext(doc ->
        {
            return in.nodeQuery(query).flatMapThrowing(node ->
            {
                Node parent = node.getParentNode();
                if (parent != null)
                {
                    Node sib = node.getNextSibling();
                    // If there is a subsequent whitespace node, remove it.
                    if (sib instanceof Text && sib.getTextContent().trim().isEmpty())
                    {
                        parent.removeChild(sib);
                    }
                    parent.removeChild(node);
                    return ThrowingOptional.of(doc);
                }
                return ThrowingOptional.empty();
            }).orElse(null);
        });
    }

    @Override
    public int hashCode()
    {
        int hash = 3;
        hash = 29 * hash + Objects.hashCode(this.in);
        hash = 29 * hash + Objects.hashCode(this.query);
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
            if (obj == null || obj.getClass() != XMLElementRemoval.class)
            {
                return false;
            }
        final XMLElementRemoval other = (XMLElementRemoval) obj;
        if (!Objects.equals(this.query, other.query))
            return false;
        return Objects.equals(this.in, other.in);
    }
    
    @Override
    public String toString() {
        return query + " REMOVAL in " + in;
    }
}
