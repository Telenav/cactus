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

import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.model.PomVersion;
import com.telenav.cactus.maven.model.internal.PomFile;
import com.telenav.cactus.maven.model.resolver.Poms;
import com.telenav.cactus.maven.xml.AbstractXMLUpdater;
import com.telenav.cactus.maven.xml.XMLReplacer;
import com.telenav.cactus.maven.xml.XMLTextContentReplacement;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.w3c.dom.Document;

import static java.util.Collections.emptySet;

/**
 * Brings all properties to a consistent version so everything is using the same
 * versions of things.
 *
 * @author Tim Boudreau
 */
public class PropertyHomogenizer
{
    private final PomCategorizer categorizer;
    private final VersionIndicatingProperties potentialPropertyChanges;
    private final Map<Pom, PropertyChange<?, PomVersion>> changes = new HashMap<>();
    private boolean pretend;

    public PropertyHomogenizer(Poms poms)
    {
        categorizer = new PomCategorizer(poms);
        potentialPropertyChanges = VersionIndicatingProperties.create(
                categorizer);
    }

    public PropertyHomogenizer pretend()
    {
        pretend = true;
        return this;
    }

    public Set<Path> go(Consumer<String> log) throws Exception
    {
        collectProperties();
        if (changes.isEmpty())
        {
            return emptySet();
        }
        Set<Path> result = new HashSet<>();
        List<AbstractXMLUpdater> replacements = new ArrayList<>();
        for (Map.Entry<Pom, PropertyChange<?, PomVersion>> e : changes
                .entrySet())
        {
            log.accept(" * " + e.getValue());
            PomFile file = PomFile.of(e.getKey());
            replacements.add(new XMLTextContentReplacement(file,
                    "/project/properties/" + e.getValue().propertyName(), e
                    .getValue().newValue().text()));
        }
        AbstractXMLUpdater.openAll(replacements, () ->
        {
            for (AbstractXMLUpdater rep : replacements)
            {
                Document doc = rep.replace();
                if (doc != null)
                {
                    if (!pretend)
                    {
                        XMLReplacer.writeXML(doc, rep.path());
                    }
                    log.accept((pretend
                                ? "(pretend) "
                                : "") + " Wrote " + rep.path());
                    result.add(rep.path());
                }
            }
            return null;
        });
        return result;
    }

    private void collectProperties()
    {
        Map<String, Set<VersionProperty<?>>> propertyInstances = new HashMap<>();
        Map<String, Set<PomVersion>> versions = new HashMap<>();
        Set<VersionProperty<?>> all = potentialPropertyChanges.all();
        for (VersionProperty<?> vp : all)
        {
            Set<PomVersion> vers = versions.computeIfAbsent(vp.property(),
                    k -> new HashSet<>());
            vers.add(PomVersion.of(vp.oldValue()));
            Set<VersionProperty<?>> props = propertyInstances.computeIfAbsent(vp
                    .property(), k -> new HashSet<>());
            props.add(vp);
        }
        versions.forEach((prop, vers) ->
        {
            if (vers.size() > 1)
            {
                LinkedList<PomVersion> candidates = new LinkedList<>(vers);
                Collections.sort(candidates);
                PomVersion best = candidates.getLast();
                for (VersionProperty<?> vp : propertyInstances.get(prop))
                {
                    if (!best.is(vp.oldValue()))
                    {
                        PropertyChange.propertyChange(vp, best).ifPresent(chg ->
                        {
                            changes.put(vp.pom(), chg);
                        });
                    }
                }
            }
        });
    }
}
