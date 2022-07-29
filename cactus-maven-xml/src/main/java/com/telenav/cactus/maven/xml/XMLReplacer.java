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

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Writes a document out to a file, making a best-effort to avoid generating
 * spurious diffs. Uses Transformer, which mostly leaves things alone (but not
 * spaces around = characters), with some hacks to ensure there is always a
 * trailing newline, and to replace the head of the document up to the opening
 * tag with that from the original document if it exists (because Transformer
 * will royally screw up the formatting of leading comments, rewrite the xml
 * declartion differently and mess with the formatting of namespace declarations
 * thoroughly).
 *
 * @author Tim Boudreau
 */
public final class XMLReplacer
{
    public static void writeXML(Document doc, Path path) throws Exception
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StreamResult res = new StreamResult(out);
        TransformerFactory tFactory
                = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
//        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), res);
        String munge = new String(out.toByteArray(), "UTF-8");
        String oldContent = null;
        if (Files.exists(path) && Files.isReadable(path))
        {
            oldContent = new String(Files.readAllBytes(path), UTF_8);
        }
        munge = restoreOriginalHead(oldContent, munge);
        Files.write(path, munge.getBytes(UTF_8), WRITE, TRUNCATE_EXISTING);
    }

    private static String restoreOriginalHead(String orig, String mangled)
    {
        if (orig == null)
        {
            if (mangled.length() > 0 && mangled.charAt(mangled.length() - 1) != '\n')
            {
                return mangled + '\n';
            }
            else
            {
                return mangled;
            }
        }
        int oix = orig.indexOf("<project");
        int nix = mangled.indexOf("<project");
        if (oix < 0 || nix < 0)
        {
            // If it's hosed, don't make it worse.
            return mangled;
        }
        int oend = orig.indexOf('>', oix + 1);
        int nend = mangled.indexOf('>', nix + 1);
        if (oend < 0 || nend < 0)
        {
            return mangled;
        }
        String oldHead = orig.substring(0, oend + 1);
        String newTail = mangled.substring(nend + 1, mangled.length());
        return oldHead + newTail + (newTail.charAt(newTail.length() - 1) == '\n'
                                    ? ""
                                    : '\n');
    }

    private XMLReplacer()
    {
        throw new AssertionError();
    }
}
