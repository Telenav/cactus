package com.telenav.cactus.maven.git;

import com.mastfrog.util.strings.Strings;
import com.telenav.cactus.maven.git.Heads.Head;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Parsed output of `git ls-remote` to list remote heads and compare them with local branch heads.
 *
 * @author Tim Boudreau
 */
public class Heads implements Iterable<Head>
{
    public static Heads from(String text)
    {
        Set<Head> heads = new HashSet<>();
        Optional<String> remoteUrl = Optional.empty();
        for (String line : text.split("\n"))
        {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#')
            {
                continue;
            }
            if (line.startsWith("From "))
            {
                remoteUrl = Optional.of(line.substring(5).trim());
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length == 2)
            {
                String hash = parts[0];
                String ref = parts[1];
                Head.from(ref, hash).ifPresent(heads::add);
            }
        }
        return new Heads(remoteUrl, heads);
    }

    public static class Head implements Comparable<Head>
    {

        public static Optional<Head> from(String ref, String hash)
        {
            String[] parts = ref.split("/");
            if (parts.length <= 2)
            {
                return Optional.empty();
            }
            for (int i = 0; i < hash.length(); i++)
            {
                if (!isHashChar(hash.charAt(i)))
                {
                    return Optional.empty();
                }
            }
            return Optional.of(new Head(parts, hash));
        }

        private final String[] refParts;

        private final String hash;

        public Head(String[] refParts, String hash)
        {
            this.refParts = refParts;
            this.hash = hash;
        }

        @Override
        public int compareTo(Head o)
        {
            int result = name().compareTo(o.name());
            if (result == 0)
            {
                result = hash.compareTo(o.hash);
            }
            if (result == 0)
            {
                result = kind().compareTo(o.kind());
            }
            return result;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj)
            {
                return true;
            }
            if (obj == null)
            {
                return false;
            }
            if (getClass() != obj.getClass())
            {
                return false;
            }
            final Head other = (Head) obj;
            if (!Objects.equals(this.hash, other.hash))
            {
                return false;
            }
            return Arrays.deepEquals(this.refParts, other.refParts);
        }

        @Override
        public int hashCode()
        {
            int result = 7;
            result = 17 * result + Arrays.deepHashCode(this.refParts);
            result = 17 * result + Objects.hashCode(this.hash);
            return result;
        }

        public boolean is(String what)
        {
            return name().equals(what);
        }

        public boolean isBranch()
        {
            return "heads".equals(kind());
        }

        public boolean isTag()
        {
            return "tags".equals(kind());
        }

        public String kind()
        {
            return refParts[1];
        }

        public String name()
        {
            StringBuilder sb = new StringBuilder();
            for (String part : refParts)
            {
                if (sb.length() > 0)
                {
                    sb.append('/');
                }
                sb.append(part);
            }
            return sb.toString();
        }

        @Override
        public String toString()
        {
            return hash + "\t" + Strings.join('/', refParts);
        }

        private static boolean isHashChar(char c)
        {
            return (c >= 'a' && c <= 'f') || (c >= '0' && c <= '9');
        }
    }

    private final Set<Head> heads;

    private final Optional<String> remoteUrl;

    Heads(Optional<String> remoteUrl, Set<Head> heads)
    {
        this.heads = heads;
        this.remoteUrl = remoteUrl;
    }

    public Optional<Head> findBranch(String name)
    {
        for (Head head : heads)
        {
            if (head.isBranch() && head.is(name))
            {
                return Optional.of(head);
            }
        }
        return Optional.empty();
    }

    public boolean isFrom(GitRemotes remote)
    {
        if (!remoteUrl.isPresent())
        {
            return false;
        }
        return remoteUrl.get().equals(remote.fetchUrl)
                || remoteUrl.get().equals(remote.pushUrl);
    }

    @Override
    public Iterator<Head> iterator()
    {
        return Collections.unmodifiableSet(heads).iterator();
    }

    public Optional<String> remoteUrl()
    {
        return remoteUrl;
    }

    @Override
    public String toString()
    {
        List<Head> all = new ArrayList<>(heads);
        Collections.sort(all);
        return Strings.join('\n', all);
    }
}
