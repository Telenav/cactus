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

package com.telenav.cactus.maven.git;

import com.telenav.cactus.maven.log.BuildLog;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tim Boudreau
 */
public class GitRemotes
{
    static Map<String, GitRemotes> from(String output)
    {
        Map<String, String> pushUrls = new HashMap<>();
        Map<String, String> fetchUrls = new HashMap<>();
        for (String line : output.split("\n"))
        {
            String[] parts = line.split("\\s+");
            if (parts.length == 3)
            {
                switch (parts[2])
                {
                    case "(push)":
                        pushUrls.put(parts[0], parts[1]);
                        break;
                    case "(fetch)":
                        fetchUrls.put(parts[0], parts[1]);
                        break;
                    default:
                        BuildLog.get()
                                .child("git-remotes")
                                .warn("Unrecognized output from "
                                        + "`git remote -v`: " + line);
                }
            }
        }
        Map<String, GitRemotes> result = new HashMap<>();
        pushUrls.forEach((name, pushUrl) ->
        {
            String fetchUrl = fetchUrls.get(name);
            if (fetchUrl != null)
            {
                GitRemotes remotes = new GitRemotes(name, pushUrl, fetchUrl);
                result.put(name, remotes);
            }
        });
        return result;
    }

    public final String name;

    public final String pushUrl;

    public final String fetchUrl;

    public GitRemotes(String name, String pushUrl, String fetchUrl)
    {
        this.name = name;
        this.pushUrl = pushUrl;
        this.fetchUrl = fetchUrl;
    }

    public void collectRemoteNames(Set<? super String> into)
    {
        collectRemoteNames(pushUrl, into);
        collectRemoteNames(fetchUrl, into);
    }

    public String name()
    {
        return name;
    }

    @Override
    public String toString()
    {
        // Emit the same format we consume
        return name + " " + fetchUrl + " (fetch)\n" + name + " " + pushUrl + " (push)";
    }

    private static void collectRemoteNames(String remoteUrl,
            Set<? super String> into)
    {
        String[] urlParts = remoteUrl.split("[/:]");
        String last = urlParts[urlParts.length - 1];
        if (last.endsWith(".git"))
        {
            last = last.substring(0, last.length() - 4);
        }
        into.add(last);
    }
}
