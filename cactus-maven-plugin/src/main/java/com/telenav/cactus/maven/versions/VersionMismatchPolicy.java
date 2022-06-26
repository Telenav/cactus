package com.telenav.cactus.maven.versions;

import com.telenav.cactus.maven.model.Pom;
import java.util.Set;

import static com.telenav.cactus.maven.versions.VersionMismatchPolicyOutcome.ABORT;

/**
 * If we are updating versions for things, and we encounter a case where we are
 * updating the version from A to B, but the version we find is X, determines
 * what to do.
 *
 * @author Tim Boudreau
 */
public interface VersionMismatchPolicy
{

    /**
     * Determine what to do in the event of a mismatch.
     *
     * @param what The pom with a version mismatch - its current version is not
     * the version we were told to expect
     * @param expectedVersionChange The version change we are supposed to be
     * making in this pom or its family
     * @param roles The roles of this pom, which may determine what action is
     * taken
     * @return An outcome
     */
    default VersionMismatchPolicyOutcome mismatchEncountered(Pom what,
            VersionChange expectedVersionChange,
            Set<PomRole> roles)
    {
        return ABORT;
    }

    VersionMismatchPolicyOutcome onMismatch(Pom what,
            VersionChange expectedVersionChange,
            Set<PomRole> roles);

}
