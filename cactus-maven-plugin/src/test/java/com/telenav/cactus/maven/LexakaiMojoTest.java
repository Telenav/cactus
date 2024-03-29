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
package com.telenav.cactus.maven;

import com.mastfrog.function.throwing.ThrowingRunnable;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.maven.tree.ProjectTree;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author timb
 */
public class LexakaiMojoTest
{
    static Path root;

    @Test
    public void testOutputFolder() throws Exception
    {
        ifRootSet(() ->
        {
            LexakaiMojo mojo = new LexakaiMojo();
            ProjectTree tree = ProjectTree.from(root).get();

            Optional<Pom> opt = tree.findProject("com.telenav.kivakit", "kivakit-core");
            if (!opt.isPresent()) {
                return;
            }
            Pom pom = opt.get();
            Path out = mojo.output(pom);
            assertEquals(out, root.resolve("kivakit-assets"),
                    "Wrong assets path " + out);
        });
    }

    static void ifRootSet(ThrowingRunnable run) throws Exception
    {
        // If we are in a standalone checkout, then these tests cannot reason
        // about where they are
        if (root != null)
        {
            run.run();
        }
    }

    @BeforeAll
    public static void setUpClass() throws URISyntaxException
    {
        // should be jonstuff/cactus/cactus-maven-plugin/target/test-classes
        // under telenav workspace
        Path pth = Paths.get(LexakaiMojoTest.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        System.out.println("loc " + pth);
        Path mavenPluginSourceRoot = pth.getParent().getParent();

        System.out.println("IT IS " + mavenPluginSourceRoot);
        GitCheckout.checkout(mavenPluginSourceRoot).flatMap(co -> co
                .submoduleRoot().toOptional())
                .ifPresent(rootCheckout ->
                {
                    // We want to filter for the following conditions:
                    //  - We are in a git submodule
                    //  - That submodule is one folder above the root
                    //    (i.e. telenav-build is not a submodule in yet another
                    //    submodule root for a project that uses it)
                    if (mavenPluginSourceRoot.startsWith(rootCheckout
                            .checkoutRoot())
                            && !mavenPluginSourceRoot.equals(rootCheckout
                                    .checkoutRoot()))
                    {
                        Path rel = rootCheckout.checkoutRoot().relativize(
                                mavenPluginSourceRoot);
                        if (rel.getNameCount() == 1)
                        {
                            root = rootCheckout.checkoutRoot();
                            System.out.println("ROOT " + root);
                        }
                    }
                });
    }

    @AfterAll
    public static void tearDownClass()
    {
    }

    @BeforeEach
    public void setUp()
    {
    }

    @AfterEach
    public void tearDown()
    {
    }

}
