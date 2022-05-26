package com.telenav.cactus.maven.git;

import com.telenav.cactus.maven.log.BuildLog;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class GitRemotes
{

    public final String name;
    public final String pushUrl;
    public final String fetchUrl;

    public GitRemotes(String name, String pushUrl, String fetchUrl)
    {
        this.name = name;
        this.pushUrl = pushUrl;
        this.fetchUrl = fetchUrl;
    }
    
    public String name() {
        return name;
    }

    public void collectRemoteNames(Set<? super String> into)
    {
        collectRemoteNames(pushUrl, into);
        collectRemoteNames(fetchUrl, into);
    }

    private static void collectRemoteNames(String remoteUrl, Set<? super String> into)
    {
        String[] urlParts = remoteUrl.split("[/:]");
        String last = urlParts[urlParts.length - 1];
        if (last.endsWith(".git"))
        {
            last = last.substring(0, last.length() - 4);
        }
        into.add(last);
    }

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

    @Override
    public String toString()
    {
        // Emit the same format we consume
        return name + " " + fetchUrl + " (fetch)\n" + name + " " + pushUrl + " (push)";
    }
}
