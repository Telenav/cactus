package com.telenav.cactus.maven.git;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents the set of branches returned by listing branches.
 *
 * @author Tim Boudreau
 */
public class Branches
{

    private final Branch currentBranch;
    private final Set<Branch> branches;

    private Branches(Branch currentBranch, Set<Branch> branches)
    {
        this.currentBranch = currentBranch;
        this.branches = branches;
    }

    public Optional<Branch> currentBranch()
    {
        return Optional.ofNullable(currentBranch);
    }

    public boolean hasRemoteForLocalOrLocalForRemote(Branch branch)
    {
        return find(branch.branchName, !branch.isLocal()).isPresent();
    }

    @Override
    public String toString()
    {
        // Output similar to what we were created with:
        StringBuilder sb = new StringBuilder();
        for (Branch branch : branches)
        {
            if (sb.length() > 0)
            {
                sb.append('\n');
            }
            if (currentBranch == branch)
            {
                sb.append("* ");
            } else
            {
                sb.append("  ");
            }
            sb.append(branch);
        }
        return sb.toString();
    }

    public boolean contains(String name)
    {
        for (Branch branch : branches)
        {
            if (name.equals(branch.name()))
            {
                return true;
            }
        }
        return false;
    }

    public Optional<Branch> find(String name, boolean local)
    {
        for (Branch branch : branches)
        {
            if (local == branch.isLocal() && name.equals(branch.branchName))
            {
                return Optional.of(branch);
            }
        }
        return Optional.empty();
    }

    public Set<Branch> localBranches()
    {
        Set<Branch> result = new TreeSet<>();
        for (Branch branch : branches)
        {
            if (branch.isLocal())
            {
                result.add(branch);
            }
        }
        return result;
    }

    public static Branches from(String output)
    {
        Set<Branch> result = new TreeSet<>();
        Branch currentBranch = null;
        for (String line : output.split("\n"))
        {
            if (line.isBlank())
            {
                continue;
            }
            boolean isCurrentBranch = line.charAt(0) == '*';
            // trim off leading space or * and separating space
            if (line.startsWith("  ") || line.startsWith("* "))
            {
                line = line.substring(2);
            }
            if (line.contains("->"))
            {
                // e.g. remotes/origin/HEAD -> origin/master - not a branch
                continue;
            }
            String remote = null;
            if (line.startsWith("remotes/"))
            {
                String[] parts = line.split("/");
                remote = parts[1];
                StringBuilder remainder = new StringBuilder();
                for (int i = 2; i < parts.length; i++)
                {
                    if (remainder.length() > 0)
                    {
                        remainder.append('/');
                    }
                    remainder.append(parts[i]);
                }
                line = remainder.toString();
            }
            Branch branch = new Branch(line.toString(), remote);
            if (isCurrentBranch)
            {
                currentBranch = branch;
            }
            result.add(branch);
        }
        return new Branches(currentBranch, result);
    }

    public static final class Branch implements Comparable<Branch>
    {

        private final String branchName;
        private final String remote;

        Branch(String branchName, String remote)
        {
            this.branchName = branchName;
            this.remote = remote;
        }

        /**
         * The name of the branch as used in git checkout -t, e.g.
         * origin/someBranch to indicate it the remote branch to track.
         *
         * @return A name
         */
        public String trackingName()
        {
            if (remote != null)
            {
                return remote + "/" + branchName;
            }
            return branchName;
        }

        @Override
        public String toString()
        {
            // Return the same form Git would emit
            if (remote != null)
            {
                return "remotes/" + remote + "/" + branchName;
            } else
            {
                return branchName;
            }
        }

        public boolean isLocal()
        {
            return remote == null;
        }

        public Optional<String> remote()
        {
            return Optional.ofNullable(remote);
        }

        public String name()
        {
            return branchName;
        }

        public boolean isSameName(Branch other)
        {
            return branchName.equals(other.branchName);
        }

        private String remoteName()
        {
            return remote == null ? "" : remote;
        }

        @Override
        public int compareTo(Branch o)
        {
            int result = branchName.compareTo(o.branchName);
            if (result == 0)
            {
                result = remoteName().compareTo(o.remoteName());
            }
            return result;
        }

        @Override
        public boolean equals(Object o)
        {
            if (o == this)
            {
                return true;
            } else if (o == null || o.getClass() != Branch.class)
            {
                return false;
            }
            Branch b = (Branch) o;
            return branchName.equals(b.branchName)
                    && Objects.equals(remote, b.remote);
        }

        @Override
        public int hashCode()
        {
            return (41 * branchName.hashCode())
                    + (remote == null ? 0 : remote.hashCode());
        }
    }
}
