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
package com.telenav.cactus.maven;

import com.telenav.cactus.maven.mojobase.BaseMojo;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.classworlds.realm.ClassRealm;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.write;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.sort;

/**
 * Some code that's useful in debugging classloading problems - off by default,
 * but keeping it for now.
 *
 * @author Tim Boudreau
 */
final class ClassloaderLog
{

    private static final boolean enabled = false;

    static void log(MavenProject prj, BaseMojo mojo)
    {
        if (!enabled)
        {
            return;
        }
        try
        {
            _log(prj, mojo);
        }
        catch (Exception | Error e)
        {
            e.printStackTrace();
        }
    }

    static void _log(MavenProject prj, BaseMojo mojo) throws Exception
    {
        final PluginDescriptor pluginDescriptor = (PluginDescriptor) mojo
                .getPluginContext().get("pluginDescriptor");
        ClassRealm classRealm = pluginDescriptor.getClassRealm();

        logUrls(classRealm, prj);
        logPackages(classRealm, prj);
    }

    private static void logPackages(ClassRealm classRealm, MavenProject prj)
            throws IOException
    {
        List<String> pkgs = new ArrayList<>();
        collectPackages(0, classRealm, prj, pkgs, new HashSet<>());
        sort(pkgs);
        StringBuilder sb = new StringBuilder();
        for (String s : pkgs)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(s);
        }
        sb.append('\n');
        Path dest = Paths.get("/tmp").resolve(
                prj.getArtifactId() + "-packages.txt");
        write(dest, sb.toString().getBytes(UTF_8),WRITE, TRUNCATE_EXISTING,CREATE);
    }

    private static void collectPackages(int depth, ClassRealm classRealm,
            MavenProject prj, List<String> pkgs, Set<ClassRealm> seen)
            throws IOException
    {
        if (!seen.add(classRealm))
        {
            return;
        }
        for (Package p : classRealm.getDefinedPackages())
        {
            pkgs.add(p.toString());
        }
        for (ClassRealm cl : classRealm.getImportRealms())
        {
            collectPackages(depth + 1, cl, prj, pkgs, seen);
        }
    }

    private static void logUrls(ClassRealm classRealm, MavenProject prj)
            throws IOException
    {
        // Log both classloader order and easily diffed sorted order
        List<String> urls = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (URL url : classRealm.getURLs())
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(url);
            urls.add(url.toString());
        }
        sb.append('\n');
        Path dest = Paths.get("/tmp").resolve(prj.getArtifactId() + "-urls.txt");
        write(dest, sb.toString().getBytes(UTF_8),WRITE, TRUNCATE_EXISTING, CREATE);

        sort(urls);
        sb.setLength(0);
        for (String u : urls)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            sb.append(u);
        }

        sb.append('\n');
        dest = Paths.get("/tmp").resolve(
                prj.getArtifactId() + "-urls-sorted.txt");
        write(dest, sb.toString().getBytes(UTF_8), WRITE,
                TRUNCATE_EXISTING, CREATE);
    }

    private ClassloaderLog()
    {
    }
}
