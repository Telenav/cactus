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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 *
 * @author timb
 */
public class XMLVersionElementAdder extends AbstractXMLUpdater
{
    private final String version;

    public XMLVersionElementAdder(XMLFile file, String version)
    {
        super(file);
        this.version = notNull("version", version);
    }

    @Override
    public Document replace() throws Exception
    {
        return in.inContext(doc ->
        {
            return in.nodeQuery("/project/artifactId").map(nd ->
            {
                String indent = "\n    ";
                Text indentNode = doc.createTextNode(indent);
                Node par = nd.getParentNode();
                Node target = nd.getNextSibling();
                if (target == null)
                {
                    target = nd;
                }
                par.insertBefore(indentNode, target);

                Element versionElement = doc.createElement("version");
                versionElement.setTextContent(version);
                par.insertBefore(versionElement, target);

                return doc;
            }).orElse(null);
        });
    }

    @Override
    public String toString()
    {
        return "add <version>" + version + "</version> in " + in;
    }

    public boolean equals(Object o)
    {
        if (o == this)
        {
            return true;
        }
        else
            if (o == null || XMLVersionElementAdder.class != o.getClass())
            {
                return false;
            }
        XMLVersionElementAdder v = (XMLVersionElementAdder) o;
        return v.in.path().equals(in.path()) && v.version.equals(version);
    }
}
