package com.telenav.cactus.maven.util;

import com.telenav.cactus.maven.model.internal.PomFile;
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

    public XMLVersionElementAdder(PomFile file, String version)
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

    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || XMLVersionElementAdder.class != o.getClass()) {
            return false;
        }
        XMLVersionElementAdder v = (XMLVersionElementAdder) o;
        return v.in.path.equals(in.path) && v.version.equals(version);
    }
}
