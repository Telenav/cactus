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
package com.telenav.cactus.maven.tree;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.ParentMavenCoordinates;
import com.telenav.cactus.maven.model.Pom;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.mastfrog.function.optional.ThrowingOptional.empty;
import static com.mastfrog.function.optional.ThrowingOptional.from;
import static com.telenav.cactus.git.GitCheckout.checkout;
import static java.nio.file.Files.exists;

/**
 * Tests that all pom files listing a parent using a relative path actually
 * point to a project that really exists and is within the same git checkout as
 * the pom, so it is buildable in a standalone checkout.
 *
 * @author Tim Boudreau
 */
public final class ParentRelativePathChecker
{

    public Set<Problem> checkTree(ProjectTree tree)
    {
        Set<Problem> problems = new LinkedHashSet<>();
        tree.allProjects().forEach(pom ->
        {
            check(pom).ifPresent(problems::add);
        });
        return problems;
    }

    public ThrowingOptional<Problem> check(Pom pom)
    {
        return pom.parent().flatMapThrowing(parentCoords ->
        {
            if (parentCoords.relativePath.isNone())
            {
                return ThrowingOptional.empty();
            }
            return parentCoords.relativePath.resolve(pom.projectFolder())
                    .flatMapThrowing(relPath ->
                    {
                        if (!exists(relPath))
                        {
                            return ThrowingOptional.of(
                                    new NonExistentParentProblem(pom,
                                            parentCoords));
                        }
                        return from(GitCheckout.checkout(pom.path()))
                                .flatMapThrowing(pomOwningCheckout ->
                                {
                                    return from(GitCheckout.checkout(relPath))
                                            .flatMapThrowing(parentCheckout ->
                                            {
                                                if (!pomOwningCheckout.equals(
                                                        parentCheckout))
                                                {
                                                    return ThrowingOptional.of(
                                                            new ParentProblem(
                                                                    pom,
                                                                    pomOwningCheckout,
                                                                    parentCheckout,
                                                                    parentCoords));

                                                }
                                                return empty();
                                            });
                                });
                    });
        });
    }

    public static class NonExistentParentProblem extends Problem
    {
        private final Pom pom;
        private final ParentMavenCoordinates parentCoords;

        public NonExistentParentProblem(Pom pom,
                ParentMavenCoordinates parentCoords)
        {
            this.pom = pom;
            this.parentCoords = parentCoords;
        }

        public Pom pom()
        {
            return pom;
        }

        public ParentMavenCoordinates parentCoordinates()
        {
            return parentCoords;
        }

        @Override
        protected String computeMessage()
        {
            return "POM " + pom.coordinates() + " has a parent relative path "
                    + "pointing to " + parentCoords.relativePath.resolve(
                            pom.projectFolder()) + " via the value "
                    + " '" + parentCoords.relativePath.text() + "' "
                    + "which does not exist.\n" + pom.path();
        }
    }

    public static class ParentProblem extends Problem
    {
        private final Pom pom;
        private final ParentMavenCoordinates parentCoords;
        private final GitCheckout expectedCheckout;
        private final GitCheckout encounteredCheckout;

        public ParentProblem(Pom pom, GitCheckout expectedCheckout,
                GitCheckout encounteredCheckout,
                ParentMavenCoordinates parentCoords)
        {
            this.pom = pom;
            this.expectedCheckout = expectedCheckout;
            this.encounteredCheckout = encounteredCheckout;
            this.parentCoords = parentCoords;
        }

        public Pom pom()
        {
            return pom;
        }

        public ParentMavenCoordinates parentCoordinates()
        {
            return parentCoords;
        }

        public GitCheckout expectedCheckout()
        {
            return expectedCheckout;
        }

        public GitCheckout encounteredCheckout()
        {
            return encounteredCheckout;
        }

        @Override
        protected String computeMessage()
        {
            return "POM " + pom.coordinates() + " has a parent relative path '" + parentCoords.relativePath
                    + (parentCoords.relativePath.isDefault()
                       ? " (because <relativePath/> is not specified and the default is ../pom.xml)"
                       : "")
                    + " which points outside it's own git checkout in " + expectedCheckout
                            .checkoutRoot()
                    + " into the checkout in " + encounteredCheckout
                            .checkoutRoot()
                    + ". Such projects will not be buildable when checkout out individually.\n"
                    + pom.path();
        }
    }
}
