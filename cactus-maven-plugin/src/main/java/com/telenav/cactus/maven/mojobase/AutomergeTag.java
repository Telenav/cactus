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
package com.telenav.cactus.maven.mojobase;

import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.cli.CliCommand;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.maven.execution.MavenSession;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.getenv;

/**
 *
 * @author Tim Boudreau
 */
public final class AutomergeTag
{
    public static final String AUTOMERGE_TAG_PREFIX = "automerge-";
    private final long timestamp;
    private final String project;
    private String host;

    AutomergeTag(MavenSession sess)
    {
        timestamp = sess.getStartTime().getTime();
        project = sess.getTopLevelProject().getArtifactId();
    }

    AutomergeTag()
    {
        timestamp = currentTimeMillis();
        project = "-none-";
    }

    @Override
    public String toString()
    {
        return AUTOMERGE_TAG_PREFIX
                + Long.toString(timestamp, 36) + "-" + host();
    }

    private String host()
    {
        return host == null
               ? host = hostString()
               : host;
    }

    private String hostString()
    {
        // Tries the usual subjects and falls back to a random number
        String result = getenv("HOST");
        if (result == null)
        {
            result = getenv("HOSTNAME");
        }
        if (result == null)
        {
            try
            {
                result = InetAddress.getLocalHost().getHostName();
            }
            catch (UnknownHostException ex)
            {
                // network config doesn't have a host name
                ex.printStackTrace();
            }
        }
        if (result == null)
        {
            try
            {
                result = CliCommand.fixed("hostname",
                        Paths.get(".")).run()
                        .awaitQuietly();
            }
            catch (Exception ex)
            {
                // okay, not available or on path or something
            }
        }
        if (result != null)
        {
            // We don't really want to leak the host name of a developer
            // machine to the universe
            result = Strings.sha1(result + project).replaceAll("=", "")
                    .replaceAll("/", "_");
        }
        if (result == null)
        {
            // As a fallback, use a reasonably collision proof value
            result = Long.toString(ThreadLocalRandom.current().nextLong(), 36);
        }
        return result;
    }
}
