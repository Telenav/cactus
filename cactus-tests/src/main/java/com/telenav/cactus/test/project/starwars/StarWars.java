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
package com.telenav.cactus.test.project.starwars;

import com.telenav.cactus.test.project.ProjectWrapper;
import com.telenav.cactus.test.project.GeneratedProjectTree;
import com.mastfrog.function.state.Obj;
import com.telenav.cactus.test.project.generator.GeneratedProjects;
import com.telenav.cactus.test.project.generator.ProjectsGenerator;
import java.io.IOException;

import static com.mastfrog.util.preconditions.Checks.notNull;

/**
 * Creates the StarWars project family tree used in a number of tests.
 *
 * @author Tim Boudreau
 */
public final class StarWars extends GeneratedProjectTree<StarWars>
{
    public static final String INTERMEDIATEBOMS = "intermediate-boms";
    public static final String WOOKIESSUPERPOM = "wookies-superpom";
    public static final String WOOKIE = "wookie";
    public static final String LIMBS = "limbs";
    public static final String FUR = "fur";
    public static final String DRINKMACHINE = "drink-machine";
    public static final String FEEDINGMACHINE = "feeding-machine";
    public static final String SLUGFARM = "slug-farm";
    public static final String SPACESHIPSSUPERPOM = "spaceships-superpom";
    public static final String EXHAUSTPORT = "exhaust-port";
    public static final String COMPONENTS = "components";
    public static final String THRUSTERS = "thrusters";
    public static final String MILLENIUMFALCON = "milleniumfalcon";
    public static final String DEATHSTAR = "deathstar";

    StarWars(GeneratedProjects projects, String groupIdBase, String uid)
    {
        super(projects, groupIdBase, uid);
    }

    public String superpomsArtifactId()
    {
        return "starwars" + uid + "-superpoms";
    }

    public ProjectWrapper superpomsProject()
    {
        return project(superpomsArtifactId());
    }

    public ProjectWrapper wookiesSuperpom()
    {
        return project(WOOKIESSUPERPOM);
    }

    public ProjectWrapper components()
    {
        return project("spaceship-" + COMPONENTS);
    }

    public ProjectWrapper spaceshipsSuperpom()
    {
        return project(SPACESHIPSSUPERPOM);
    }

    public ProjectWrapper exhaustPort()
    {
        return project("spaceship-" + EXHAUSTPORT);
    }

    public ProjectWrapper feedingMachine()
    {
        return project("wookies-" + FEEDINGMACHINE);
    }

    public ProjectWrapper drinkMachine()
    {
        return project("wookies-" + DRINKMACHINE);
    }

    public ProjectWrapper milleniumFalcon()
    {
        return project("spaceship-" + MILLENIUMFALCON);
    }

    public ProjectWrapper deathstar()
    {
        return project("spaceship-" + DEATHSTAR);
    }

    public ProjectWrapper thrusters()
    {
        return project("spaceship-" + THRUSTERS);
    }

    public ProjectWrapper slugfarm()
    {
        return project("wookies-" + SLUGFARM);
    }

    public ProjectWrapper intermediateBoms()
    {
        return project(INTERMEDIATEBOMS);
    }

    public ProjectWrapper wookie()
    {
        return project(WOOKIE);
    }

    public ProjectWrapper fur()
    {
        return project("wookies-" + FUR);
    }

    public ProjectWrapper limbs()
    {
        return project("wookies-" + LIMBS);
    }

    public static StarWars starWars() throws IOException
    {
        return starWars(uniquifier());
    }

    @Override
    public StarWars newClone() throws IOException
    {
        GeneratedProjects clone = notNull("clone", projects.newClone());
        return new StarWars(clone, groupIdBase, uid);
    }

    public static StarWars starWars(String uid) throws IOException
    {
        String groupId = "com.starwars" + uid;
        ProjectsGenerator fake = new ProjectsGenerator(groupId);

        fake.superpom("1.0.0", iparentBuilder ->
        {
            iparentBuilder.addProperty("stuff", "stuff");
            iparentBuilder.withPackaging("pom");
            iparentBuilder.withArtifactId(INTERMEDIATEBOMS)
                    .setAsIntermediatePomParent();
        });

        fake.superpom("1.0.0", baseBuilder ->
        {
            baseBuilder.addProperty("maven.compiler.source", "11");
            baseBuilder.addProperty("maven.compiler.target", "11");
            baseBuilder.addProperty("maven.compiler.release", "11");
            baseBuilder.addProperty("guice.version", "4.2.2");
            baseBuilder.addDependencyManagement("com.google.inject", "guice",
                    "${guice.version}");

            ProjectsGenerator.Superpom base = baseBuilder.withArtifactId("root");

            fake.superpom("2.0.0", supBuilder ->
            {
                supBuilder.setParent(base);
                supBuilder.withPackaging("pom");
                supBuilder.addProperty("wookies.prev.version", "1.5.0");
                supBuilder.addProperty("wookies.version", "1.5.1");
                ProjectsGenerator.Superpom sup = supBuilder.withArtifactId(
                        WOOKIESSUPERPOM)
                        .forceGroupId("wookies");
                Obj<ProjectsGenerator.FakeProject> wookiesPrj = Obj.create();
                fake.family("wookies", "1.5.1", fam ->
                {
                    fam.subfamily("sustenance", sub ->
                    {
                        sub.project(prj ->
                        {
                            prj.setParent(sup);
                            prj.addProperty("skiddoo", "22");

                            ProjectsGenerator.FakeProject farm = prj
                                    .withArtifactId(SLUGFARM);
                            supBuilder.addDependencyManagement(farm);

                            fam.subfamily("feeding", feeding ->
                            {
                                feeding.project(
                                        (ProjectsGenerator.FamilyBuilder.SubfamilyBuilder.ProjectBuilder feed) ->
                                {
                                    feed.setParent(sup);
                                    feed.addDependency("com.google.inject",
                                            "guice");
                                    feed.addDependency(farm);
                                    ProjectsGenerator.FakeProject feedPrj = feed
                                            .withArtifactId(FEEDINGMACHINE);
                                    supBuilder.addDependencyManagement(feedPrj);
                                });

                                feeding.project(
                                        (ProjectsGenerator.FamilyBuilder.SubfamilyBuilder.ProjectBuilder drink) ->
                                {
                                    drink.setParent(sup);
                                    drink.addDependency("com.google.inject",
                                            "guice");
                                    ProjectsGenerator.FakeProject drinkPrj = drink
                                            .withArtifactId(DRINKMACHINE);
                                    supBuilder.addDependencyManagement(drinkPrj);
                                });
                                fam.subfamily("", wookies ->
                                {
                                    wookies.project(furBuilder ->
                                    {
                                        furBuilder
                                                .addProperty("furLengthInches",
                                                        "1");
                                        furBuilder.withSubpath(
                                                "anatomy/external");
                                        furBuilder.setParent(sup);
                                        ProjectsGenerator.FakeProject fur = furBuilder
                                                .withArtifactId(FUR);
                                        supBuilder.addDependencyManagement(fur);

                                        wookies.project(limbsBuilder ->
                                        {
                                            limbsBuilder.setParent(sup);
                                            limbsBuilder.withSubpath(
                                                    "anatomy/external");
                                            ProjectsGenerator.FakeProject limbs = limbsBuilder
                                                    .withArtifactId(LIMBS);
                                            supBuilder
                                                    .addDependencyManagement(
                                                            limbs);

                                            wookies.project(wookieBuilder ->
                                            {
                                                wookieBuilder.addDependency(fur);
                                                wookieBuilder.addDependency(
                                                        limbs);
                                                wookieBuilder.setParent(sup);
                                                ProjectsGenerator.FakeProject wookieProject = wookieBuilder
                                                        .withArtifactId(WOOKIE);

                                                wookiesPrj.set(wookieProject);
                                                supBuilder
                                                        .addDependencyManagement(
                                                                wookieProject);
                                            });
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
                fake.superpom("3.1.5", spSup ->
                {
                    spSup.importing(sup);
                    spSup.setParent(base);
                    spSup.addProperty("spaceships.version", "3.1.5");
                    ProjectsGenerator.Superpom spSuperpom = spSup
                            .withArtifactId(SPACESHIPSSUPERPOM).forceGroupId(
                            "spaceships");
                    fake.family("spaceship", "3.1.5", fam ->
                    {
                        fam.subfamily("", sub ->
                        {
                            sub.project(compBuilder ->
                            {
                                compBuilder.addProperty("exhaustPorts", "2");
                                compBuilder.withSubpath(COMPONENTS);
                                compBuilder.setParent(spSuperpom);
                                ProjectsGenerator.FakeProject exhaustPortPrj = compBuilder
                                        .withArtifactId(EXHAUSTPORT);
                                spSup.addDependencyManagement(exhaustPortPrj);

                                sub.project(thr ->
                                {
                                    thr.setParent(spSuperpom);
                                    thr.withSubpath(COMPONENTS);
                                    ProjectsGenerator.FakeProject thrustersProject = thr
                                            .withArtifactId(THRUSTERS);
                                    spSup.addDependencyManagement(
                                            thrustersProject);

                                    sub.project(prj ->
                                    {
                                        prj.setParent(spSuperpom);
                                        prj.addDependency(wookiesPrj.get()
                                                .toArtifactIdentifiers());
                                        prj.addDependency(thrustersProject);
                                        ProjectsGenerator.FakeProject mfProject = prj
                                                .withArtifactId(MILLENIUMFALCON);
                                        spSup.addDependencyManagement(mfProject);
                                    });
                                    sub.project(prj ->
                                    {
                                        prj.setParent(spSuperpom);
                                        prj.addDependency(exhaustPortPrj
                                                .toArtifactIdentifiers());
                                        ProjectsGenerator.FakeProject deathStar = prj
                                                .withArtifactId(DEATHSTAR);
                                        spSup.addDependencyManagement(deathStar);
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });

        GeneratedProjects x = fake.build();
        if (x == null) {
            // Debugging continuous build failure
            throw new IllegalStateException("Generated projects is null from " 
                    + fake);
        }
        // We need a clone here, or we will be writing into the workspace
        // we want to push into
        StarWars result = new StarWars(x, groupId, uid);
        return result;
    }

}
