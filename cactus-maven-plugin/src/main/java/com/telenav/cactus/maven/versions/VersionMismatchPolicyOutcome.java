/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Enum.java to edit this template
 */
package com.telenav.cactus.maven.versions;

import com.telenav.cactus.maven.model.Pom;
import java.util.Set;

/**
 *
 * @author timb
 */
public enum VersionMismatchPolicyOutcome implements VersionMismatchPolicy {
    /**
     * Just do nothing to the non-matching pom.
     */
    SKIP,
    /**
     * Clobber the version with whatever it's supposed to be regardless of
     * its current value.
     */
    BRING_TO_TARGET_VERSION,
    /**
     * Bump the version, using the change for the family (if any) as a basis
     * (magnitude, flavor).
     */
    BUMP,
    /**
     * Throw an exception and don't proceed in the case of this mismatch.
     */
    ABORT;

    @Override
    public VersionMismatchPolicyOutcome onMismatch(Pom what,
            VersionChange expectedVersionChange, Set<PomRole> roles)
    {
        return this;
    }
    
}
