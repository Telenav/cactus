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
package com.telenav.cactus.maven.refactoring;

import com.mastfrog.function.state.Bool;
import com.mastfrog.util.preconditions.Exceptions;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.GroupId;
import com.telenav.cactus.maven.model.MavenIdentified;
import com.telenav.cactus.maven.model.MavenVersioned;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.VersionChange;
import com.telenav.cactus.maven.model.VersionChangeMagnitude;
import com.telenav.cactus.maven.model.VersionFlavorChange;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.published.PublishChecker;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLElementRemoval;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import com.telenav.cactus.maven.xml.XMLVersionElementAdder;
import com.telenav.cactus.scope.ProjectFamily;
import com.telenav.cactus.util.SectionedMessage;
import com.telenav.cactus.util.SectionedMessage.MessageSection;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import org.w3c.dom.Document;

import static com.mastfrog.util.preconditions.Checks.notNull;
import static com.telenav.cactus.maven.xml.XMLReplacer.writeXML;
import static java.util.Collections.emptySet;

/**
 * Accepts a mapping of versions to change for individual POM files and/or
 * families, and will pinpoint all of the files that need changes, including
 * property updates as long as they follow one of the following patterns:
 * <ul>
 * <li>$FAMILY.version</li>
 * <li>$FAMILY.prev.version</li>
 * <li>$FAMILY.previous.version</li>
 * <li>$ARTIFACT_ID.version</li>
 * <li>s/$ARTIFACT_ID/-/..version</li>
 * <li>$FAMILY.$ARTIFACT_ID.version</li>
 * <li>$ARTIFACT_ID.prev.version</li>
 * <li>s/$ARTIFACT_ID/-/..prev.version</li>
 * </ul>
 *
 * @author Tim Boudreau
 */
public class VersionReplacementFinder
{
    private final PomCategorizer categories;
    private final VersionIndicatingProperties potentialPropertyChanges;
    private final Map<ProjectFamily, VersionChange> familyVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> pomVersionChanges = new HashMap<>();
    private final Map<Pom, VersionChange> parentVersionChanges = new HashMap<>();
    private final Map<Pom, Set<PropertyChange<?, PomVersion>>> propertyChanges = new HashMap<>();
    private final Set<Pom> removeExplicitVersionFrom = new HashSet<>();
    private final Set<Pom> versionMismatches = new HashSet<>();
    private SuperpomBumpPolicy superpomBumpPolicy = SuperpomBumpPolicy.BUMP_WITHOUT_CHANGING_FLAVOR;
    private VersionMismatchPolicy versionMismatchPolicy
            = VersionMismatchPolicyOutcome.ABORT;
    private boolean pretend;
    private VersionUpdateFilter filter;
    private boolean needResolve = true;
    private boolean bumpAlreadyPublishedPoms;
    private PublishChecker publishChecker = new PublishChecker();

    public VersionReplacementFinder(Poms poms)
    {
        categories = new PomCategorizer(poms);
        potentialPropertyChanges = VersionIndicatingProperties
                .create(categories);
    }

    public VersionReplacementFinder withPublishChecker(PublishChecker checker)
    {
        this.publishChecker = notNull("checker", checker);
        return this;
    }

    public VersionReplacementFinder withSuperpomBumpPolicy(
            SuperpomBumpPolicy policy)
    {
        this.superpomBumpPolicy = notNull("policy", policy);
        return this;
    }

    public VersionReplacementFinder withVersionMismatchPolicy(
            VersionMismatchPolicy policy)
    {
        this.versionMismatchPolicy = notNull("policy", policy);
        return this;
    }

    public VersionReplacementFinder pretend(boolean pretendMode)
    {
        this.pretend = pretendMode;
        return this;
    }

    public VersionReplacementFinder withFilter(VersionUpdateFilter filter)
    {
        if (!needResolve)
        {
            throw new IllegalStateException("Cannot set filter at this point");
        }
        this.filter = filter;
        needResolve = true;
        return this;
    }

    public VersionReplacementFinder withSinglePomChange(
            ArtifactId artifactId, PomVersion newVersion)
    {
        needResolve = true;
        return categories.poms().get(artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public VersionReplacementFinder bumpUnpublishedPoms()
    {
        this.bumpAlreadyPublishedPoms = true;
        return this;
    }

    public Optional<VersionChange> versionChangeFor(Pom pom)
    {
        return Optional.ofNullable(pomVersionChanges.get(pom));
    }

    public VersionReplacementFinder withSinglePomChange(
            ArtifactId artifactId, GroupId group, PomVersion newVersion)
    {
        needResolve = true;
        return categories.poms().get(group, artifactId).map(pom ->
        {
            return withSinglePomChange(pom, newVersion);
        }).orElse(this);
    }

    public <P extends MavenIdentified & MavenVersioned> VersionReplacementFinder
            withSinglePomChange(P what, PomVersion newVersion)
    {
        needResolve = true;
        Consumer<Pom> c = pom ->
        {
            if (!pom.version().equals(newVersion))
            {
                pomVersionChanges.put(pom, new VersionChange(pom.version(),
                        newVersion));
            }
        };
        if (what instanceof Pom)
        {
            c.accept((Pom) what);
        }
        else
        {
            categories.poms().get(what).toOptional().ifPresent(c);
        }
        return this;
    }

    public VersionReplacementFinder withFamilyVersionChange(ProjectFamily family,
            PomVersion old, PomVersion nue)
    {
        needResolve = true;
        VersionChange vc = new VersionChange(old, nue);
        familyVersionChanges.put(family, vc);
        return this;
    }

    /**
     * Main entry point for computing version changes.
     */
    private void resolveVersionMismatchesAndFinalizeUpdateSet()
    {
        if (!needResolve)
        {
            // We already ran, no need to do it again as some of it is
            // expensive.  The work is idempotent, but pointless to do
            // twice (toString() also calls this method to make sure the
            // description of what we're going to do is accurate).
            return;
        }

        // The VersionChangeUpdatesCollector we pass changes to, and it
        // records whether or not there was an actual change to the stored
        // values that represent what we're going to do.
        //
        // Its hasChanges() resets the changed state.
        //
        // We need to run this iteratively until no new changes have been
        // added, because each round may add changes to additional poms
        // which have children, so those children get the fact that their
        // parent version needs updating recorded in the next round, and
        // so forth, until no change has been made
        Set<Pom> conflicted = collectConflictPoms();

        VersionUpdateFinder finder = new VersionUpdateFinder(changeCollector(),
                categories,
                potentialPropertyChanges,
                familyVersionChanges, 
                superpomBumpPolicy, 
                versionMismatchPolicy,
                publishChecker);
        finder.go();
        // We want to let it do any changes that are dictated by policy first,
        // and then if there are still conflicted poms that have not been
        // bumped, only bump those.
        if (this.bumpAlreadyPublishedPoms && !conflicted.isEmpty())
        {
            conflicted.removeAll(this.pomVersionChanges.keySet());
            conflicted.removeAll(this.parentVersionChanges.keySet());
            // Ensure the remaining versions are bumped
            if (!conflicted.isEmpty())
            {
                Bool changes = Bool.create();
                for (Pom p : conflicted)
                {
                    p.version().updatedWith(
                            VersionChangeMagnitude.DOT,
                            VersionFlavorChange.UNCHANGED).ifPresent(v ->
                            {
                                p.version().to(v).ifPresent(vv ->
                                {
                                    pomVersionChanges.put(p, vv);
                                    changes.set();
                                });

                            });
                }
                if (changes.getAsBoolean())
                {
                    finder.go();
                }
            }
        }
        needResolve = false;
    }

    private Set<Pom> collectConflictPoms()
    {
        if (!bumpAlreadyPublishedPoms)
        {
            return emptySet();
        }
        try
        {
            Set<Pom> result = new HashSet<>();
            for (Pom p : categories.allPoms())
            {
                switch (publishChecker.check(p).state())
                {
                    case PUBLISHED_DIFFERENT:
                        result.add(p);
                        break;
                }
            }
            return result;
        }
        catch (IOException | InterruptedException | URISyntaxException ex)
        {
            return Exceptions.chuck(ex);
        }
    }

    private VersionChangeUpdatesCollector changeCollector()
    {
        return new VersionChangeUpdatesCollector(pomVersionChanges,
                parentVersionChanges, propertyChanges,
                removeExplicitVersionFrom,
                versionMismatches, filter);

    }

    private Set<AbstractXMLUpdater> xmlUpdaters()
    {
        resolveVersionMismatchesAndFinalizeUpdateSet();
        Set<AbstractXMLUpdater> replacers = new LinkedHashSet<>();

        // Create <version> tag removers for poms where the value is now
        // superfluous
        removeExplicitVersionFrom.forEach(removeVersionFrom ->
        {
            replacers.add(new XMLElementRemoval(PomFile.of(removeVersionFrom),
                    "/project/version"));
        });

        // Add our property changes
        this.propertyChanges.forEach((pom, changes) ->
        {
            changes.forEach(change ->
            {
                replacers.add(new XMLTextContentReplacement(PomFile.of(pom),
                        "/project/properties/" + change.propertyName(),
                        change.newValue().text()));
            });
        });

        // Add out pom version tag changes
        this.pomVersionChanges.forEach((pom, versionChange) ->
        {
            if (pom.hasExplicitVersion())
            {
                String query = "/project/version";
                replacers.add(new XMLTextContentReplacement(
                        PomFile.of(pom),
                        query,
                        versionChange.newVersion().text()));
            }
            else
            {
                replacers.add(new XMLVersionElementAdder(PomFile.of(pom),
                        versionChange.newVersion().text()));
            }
        });
        // Add our parent version changes
        this.parentVersionChanges.forEach((pom, versionChange) ->
        {
            String query = "/project/parent/version";
            replacers.add(new XMLTextContentReplacement(
                    PomFile.of(pom),
                    query,
                    versionChange.newVersion().text()));
        });
        return replacers;
    }

    /**
     * Rewrite pom files.
     *
     * @return
     * @throws Exception
     */
    public Set<Path> go() throws Exception
    {
        return go(System.out::println);
    }

    public Set<Path> go(Consumer<String> msgs) throws Exception
    {
        List<AbstractXMLUpdater> replacers = new ArrayList<>(xmlUpdaters());
        // Ensure a consistent order for the sanity of anyone reading a log
        // repeatedly.
        Collections.sort(replacers);
        try
        {
            // Preload Document instances for all of the poms, so each document
            // change operates against any earlier changes
            return AbstractXMLUpdater.openAll(replacers, () ->
            {
                Set<Path> result = new HashSet<>();
                Map<Path, Document> docForPath = new HashMap<>();
                for (AbstractXMLUpdater rep : replacers)
                {
                    Document changed = rep.replace();
                    if (changed != null)
                    {
                        Document old = docForPath.get(rep.path());
                        if (old != changed && old != null)
                        {
                            throw new IllegalStateException(
                                    "Context did not hold - " + old + " vs "
                                    + changed + " for " + rep.path());
                        }
                        msgs.accept(" CHANGE: " + rep);
                        docForPath.put(rep.path(), changed);
                    }
                }
                for (Map.Entry<Path, Document> e : docForPath.entrySet())
                {
                    msgs.accept("Rewrite " + e.getKey() + (pretend
                                                           ? " (pretend)"
                                                           : ""));
                    if (!pretend)
                    {
                        writeXML(e.getValue(), e.getKey());
                    }
                    result.add(e.getKey());
                }
                return result;
            });
        }
        finally
        {
            // Dump our cached values
            categories.poms().reload();
        }
    }

    public int changeCount()
    {
        return changeCollector().allChangedPoms().size();
    }

    /**
     * Construct changes for a commit message, calling the consumer once per
     * change.
     *
     * @param c A function that can be passed a section heading and be returned
     * a consumer for that section
     */
    public void collectChanges(
            SectionedMessage c)
    {
        if (!pomVersionChanges.isEmpty())
        {
            try ( MessageSection<?> versionChanges = c
                    .section("Version Changes"))
            {
                // Use treemap for consistent sort
                new TreeMap<>(pomVersionChanges).forEach((pom, vc) ->
                {
                    versionChanges.bulletPoint(
                            pom.toArtifactIdentifiers() + ": " + vc);
                });
            }
        }
        if (parentVersionChanges.isEmpty())
        {
            try ( MessageSection<?> parentVersionChangeC = c
                    .section("Parent Version Changes"))
            {
                new TreeMap<>(parentVersionChanges).forEach((pom, vc) ->
                {
                    parentVersionChangeC.bulletPoint(
                            pom.toArtifactIdentifiers() + ": " + vc);
                });
            }
        }
        if (!propertyChanges.isEmpty())
        {
            try ( MessageSection<?> propertyChangeC = c.section(
                    "Property Changes"))
            {
                new TreeMap<>(propertyChanges).forEach((pom, changes) ->
                {
                    propertyChangeC.bulletPoint(pom.toArtifactIdentifiers());
                    changes.forEach(propC ->
                    {
                        propertyChangeC.bulletPoint(2, "*" + propC
                                .propertyName() + "*"
                                + " -\t`" + propC.oldValue() + "` \u27F6 `" + propC
                                .newValue() + "`");
                    });
                });
            }
        }
    }

    @Override
    public String toString()
    {
        Set<AbstractXMLUpdater> replacers = xmlUpdaters();
        StringBuilder sb = new StringBuilder("Version Replacements:\n");
        sb.append("FAMILIES:\n");
        familyVersionChanges.forEach((fam, ver) ->
        {
            sb.append(" * ").append(fam).append(" -> ").append(ver).append('\n');
        });
        if (!pomVersionChanges.isEmpty())
        {
            sb.append("POMS:\n");
            pomVersionChanges.forEach((pom, ver) ->
            {
                sb.append(" * ").append(pom.coordinates()
                        .toArtifactIdentifiers())
                        .append(" -> ").append(ver).append('\n');
            });
        }

        if (!parentVersionChanges.isEmpty())
        {
            sb.append("PARENT CHANGES:\n");
            parentVersionChanges.forEach((pom, ver) ->
            {
                sb.append(" * ").append(pom.coordinates()
                        .toArtifactIdentifiers())
                        .append(" ->-> ").append(ver).append('\n');
            });
        }

        if (!potentialPropertyChanges.isEmpty())
        {
            sb.append("PROPERTIES REPRESENTING VERSIONS:\n");
            sb.append(potentialPropertyChanges);
        }

        if (!propertyChanges.isEmpty())
        {
            sb.append("\nPROPERTY CHANGES:\n");
            propertyChanges.forEach((pom, change) ->
            {
                sb.append(" * ").append(change).append('\n');
            });
        }
        if (!removeExplicitVersionFrom.isEmpty())
        {
            sb.append("\nREMOVE SUPERFLUOUS VERSIONS FROM:\n");
            removeExplicitVersionFrom.forEach(pom ->
            {
                sb.append(" * ")
                        .append(pom.toArtifactIdentifiers())
                        .append(' ')
                        .append(pom.path())
                        .append('\n');
            });
        }

        if (!categories.rolesForPom().isEmpty())
        {
            sb.append("ROLES:\n");
            categories.eachPomAndItsRoles((Pom pom, Set<PomRole> roles) ->
            {
                String par = categories.parentOf(pom)
                        .map(p -> p.artifactId()
                        .toString())
                        .orElse("");

                sb.append(" * ").append(pom.toArtifactIdentifiers()).append(' ')
                        .append(roles)
                        .append(versionMismatches.contains(pom)
                                ? " **VERSION-MISMATCH** " + pom.version()
                                : "")
                        .append(" parent ").append(par)
                        .append('\n');
            });
        }
        if (!replacers.isEmpty())
        {
            sb.append("\n-------------- REPLACERS ----------------\n");
            replacers.forEach(rep -> sb.append(" * ").append(rep).append("\n"));
        }

        return sb.toString();
    }
}
