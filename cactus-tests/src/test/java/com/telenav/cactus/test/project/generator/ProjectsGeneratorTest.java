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
package com.telenav.cactus.test.project.generator;

import com.mastfrog.function.optional.ThrowingOptional;
import com.telenav.cactus.git.NeedPushResult;
import com.telenav.cactus.maven.model.ArtifactId;
import com.telenav.cactus.maven.model.Pom;
import com.telenav.cactus.scope.Scope;
import com.telenav.cactus.test.project.ProjectWrapper;
import com.telenav.cactus.test.project.starwars.StarWars;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static com.telenav.cactus.git.NeedPushResult.NO;
import static com.telenav.cactus.git.NeedPushResult.YES;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.*;
import static com.telenav.cactus.scope.Scope.ALL;
import static com.telenav.cactus.scope.Scope.ALL_PROJECT_FAMILIES;
import static com.telenav.cactus.scope.Scope.FAMILY_OR_CHILD_FAMILY;
import static com.telenav.cactus.scope.Scope.JUST_THIS;
import static com.telenav.cactus.test.project.generator.Scenarios.MODIFY_POM_IN_DEATHSTAR;
import static com.telenav.cactus.test.project.generator.StarWarsHarness.*;
import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Generates an entire tree of projects not unlike telenav's, and then runs
 * various maven targets against them.
 *
 * @author Tim Boudreau
 */
@Execution(ExecutionMode.CONCURRENT)
public class ProjectsGeneratorTest
{
    private StarWarsHarness harness;
    private StarWars starwars;

    // Pass true as the first arg to runTest to log all maven output.
    //
    // runtest() also serves to capture errors and use the fact of erroring
    // to decide whether to delete the generated repositories or leave them
    // for examination.
    @Test
    public void testPushAndPullWithCactus() throws Exception
    {
        harness.runTest(false, this::_testPushAndPullWithCactus);
    }

    @Test
    public void testPushAndPullWithSeparateCommitAndPushUsingCactusFamilyScope()
            throws Exception
    {
        harness.runTest(false,
                this::_testPushAndPullWithSeparateCommitAndPushUsingCactusFamilyScope);
    }

    @Test
    public void testBasicPushAndPull() throws Exception
    {
        harness.runTest(false, this::_testBasicPushAndPull);
    }

    @Test
    public void testPushJustThisReallyPushesJustThis() throws Exception
    {
        harness.runTest(false, this::_testPushJustThisReallyPushesJustThis);
    }

    @Test
    public void testBranchesArePickedUp() throws Exception
    {
        harness.runTest(false, this::_testBranchesArePickedUp);
    }

    @Test
    public void testCommitMojoWillNotPushIfConflict() throws Exception
    {
        harness.runTest(false, this::_testCommitMojoWillNotPushIfConflict);
    }

    @Test
    public void testPushAndPullWithSeparateCommitAndPushUsingCactusAllProjectFamiliesScope()
            throws Exception
    {
        harness.runTest(false,
                this::_testPushAndPullWithSeparateCommitAndPushUsingCactusAllProjectFamiliesScope);
    }

    @Test
    public void testSimpleBumpProjectVersion() throws Exception
    {
        harness.runTest(false, this::_testSimpleBumpProjectVersion);
    }

    @Test
    public void testAutomergeTagsGeneratedByCommitMojo() throws Exception
    {
        harness.runTest(false, this::_testAutomergeTagsGeneratedByCommitMojo);
    }

    @Test
    public void testAutomergeTagsGeneratedByAutoMergeTagMojoWithAutopush()
            throws Exception
    {
        harness.runTest(false,
                this::_testAutomergeTagsGeneratedByAutoMergeTagMojoWithAutopush);
    }

    @Test
    public void testAutomergeTagsGeneratedByAutoMergeTagMojoWithExplictPush()
            throws Exception
    {
        harness.runTest(false,
                this::_testAutomergeTagsGeneratedByAutoMergeTagMojoWithExplictPush);
    }

    private void _testPushAndPullWithSeparateCommitAndPushUsingCactusFamilyScope()
            throws Exception
    {
        StarWars clone = anotherClone();
        Path barbules1 = harness.run(Scenarios.CREATE_NEW_SOURCE_IN_FUR);
        Path sippyCup1 = harness.run(
                Scenarios.CREATE_NEW_SOURCE_IN_DRINK_MACHINE);
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drink = starwars.drinkMachine();
        harness.run(MODIFY_POM_IN_DEATHSTAR);

        ProjectWrapper unrelatedProject = starwars.deathstar();

        boolean committed = fur.runCactusTarget("commit", args(
                SCOPE, Scope.FAMILY,
                PUSH, false,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));
        assertTrue(committed, "Commit failed");

        assertTrue(unrelatedProject.getCheckout().isDirty(),
                "After commit of a different "
                + "project family, status of unrelated project checkout should still be dirty");

        assertFalse(fur.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(fur.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(drink.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        NeedPushResult np1 = fur.getCheckout().needsPush();
        assertNotNull(np1);
        assertSame(YES, np1);
        NeedPushResult np2 = drink.getCheckout().needsPush();
        assertSame(YES, np2);

        NeedPushResult npRoot = starwars.getCheckout().needsPush();
        assertSame(YES, npRoot,
                "Root checkout should also need push.");

        fur.runCactusTarget("push", args(
                SCOPE, Scope.FAMILY,
                INCLUDE_ROOT, true
        ));

        NeedPushResult np3 = fur.getCheckout().needsPush();
        assertSame(NO, np3);
        NeedPushResult np4 = drink.getCheckout().needsPush();
        assertSame(NO, np4);

        NeedPushResult npRoot2 = starwars.getCheckout().needsPush();
        assertSame(NO, npRoot2,
                "Root checkout was not pushed.");

        ProjectWrapper fur2 = clone.fur();
        ProjectWrapper drink2 = clone.drinkMachine();

        NeedPushResult np5 = fur2.getCheckout().needsPush();
        assertSame(NO, np5);
        NeedPushResult np6 = drink2.getCheckout().needsPush();
        assertSame(NO, np6);

        assertFalse(fur2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");
        assertFalse(drink2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");
        fur2.getCheckout().fetchAll();
        drink2.getCheckout().fetchAll();
        assertTrue(fur2.getCheckout().needsPull(), "Needs pull not detected");
        assertTrue(drink2.getCheckout().needsPull(), "Needs pull not detected");

        fur2.runCactusTarget("pull", args(
                SCOPE, Scope.FAMILY,
                FAMILIES, WOOKIES_FAMILY,
                INCLUDE_ROOT, true
        ));

        Path barbules2 = fur2.sourceFile("Barbules");
        assertTrue(exists(barbules2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + barbules1 + "\nclone should be in\n" + barbules2);

        Path sippyCup2 = drink2.sourceFile("SippyCup");
        assertTrue(exists(sippyCup2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + sippyCup1 + "\nclone should be in\n" + sippyCup2
                + " - if we got here, then some repositories were pushed, but not all."
        );
        assertTrue(unrelatedProject.getCheckout().isDirty(),
                "After commit/push/pull of a different "
                + "project family, status of unrelated project checkout should still be dirty");
    }

    private void _testPushAndPullWithCactus() throws Exception
    {
        StarWars clone = anotherClone();

        Path barbules1 = harness.run(Scenarios.CREATE_NEW_SOURCE_IN_FUR);
        Path sippyCup1 = harness.run(
                Scenarios.CREATE_NEW_SOURCE_IN_DRINK_MACHINE);
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drink = starwars.drinkMachine();

        boolean committed = fur.runCactusTarget("commit", args(
                SCOPE, Scope.FAMILY,
                PUSH, true,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));

        assertTrue(committed, "Commit failed");

        assertFalse(fur.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(fur.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(drink.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        assertEquals(fur.getCheckout().head(),
                fur.getCheckout().remoteHead().get(),
                "Remote head differs - push did not happen.");
        assertEquals(drink.getCheckout().head(),
                drink.getCheckout().remoteHead().get(),
                "Remote head differs - push did not happen.");

        ProjectWrapper fur2 = clone.fur();
        ProjectWrapper drink2 = clone.drinkMachine();

        fur2.runCactusTarget("pull", args(
                SCOPE, Scope.FAMILY,
                FAMILIES, WOOKIES_FAMILY,
                INCLUDE_ROOT, true
        ));

        Path barbules2 = fur2.sourceFile("Barbules");
        assertTrue(exists(barbules2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + barbules1 + "\nclone should be in\n" + barbules2);

        Path sippyCup2 = drink2.sourceFile("SippyCup");
        assertTrue(exists(sippyCup2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + sippyCup1 + "\nclone should be in\n" + sippyCup2
                + " - if we got here, then some repositories were pushed, but not all."
        );
    }

    private void _testBasicPushAndPull() throws Exception
    {
        StarWars clone = anotherClone();

        Path created = harness.run(Scenarios.CREATE_NEW_SOURCE_IN_FUR);

        ProjectWrapper fur = starwars.fur();

        fur.commit("Adding barbules.");
        fur.push();

        ProjectWrapper fur2 = clone.fur();

        assertNotEquals(fur.path(), fur2.path(), "Test harness is broken "
                + "- second clone is over same folder");

        fur2.pull();

        Path file = fur2.sourceFile("Barbules");
        assertTrue(exists(file),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + created + "\nclone should be in\n" + file
                + "\nThe test harness is broken in some way.");
    }

    private void _testPushAndPullWithSeparateCommitAndPushUsingCactusAllProjectFamiliesScope()
            throws Exception
    {
        StarWars clone = anotherClone();

        Path barbules1 = harness.run(Scenarios.CREATE_NEW_SOURCE_IN_FUR);
        Path sippyCup1 = harness.run(
                Scenarios.CREATE_NEW_SOURCE_IN_DRINK_MACHINE);
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drink = starwars.drinkMachine();

        fur.runCactusTarget("commit", args(SCOPE, ALL_PROJECT_FAMILIES,
                PUSH, false,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));

        assertFalse(fur.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(fur.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(drink.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        NeedPushResult np1 = fur.getCheckout().needsPush();
        assertNotNull(np1);
        assertSame(YES, np1);
        NeedPushResult np2 = drink.getCheckout().needsPush();
        assertSame(YES, np2);

        NeedPushResult npRoot = starwars.getCheckout().needsPush();
        assertSame(YES, npRoot,
                "Root checkout should also need push.");

        fur.runCactusTarget("push", args(SCOPE, ALL_PROJECT_FAMILIES,
                INCLUDE_ROOT, true
        ));

        NeedPushResult np3 = fur.getCheckout().needsPush();
        assertSame(NO, np3);
        NeedPushResult np4 = drink.getCheckout().needsPush();
        assertSame(NO, np4);

        NeedPushResult npRoot2 = starwars.getCheckout().needsPush();
        assertSame(NO, npRoot2,
                "Root checkout was not pushed.");

        ProjectWrapper fur2 = clone.fur();
        ProjectWrapper drink2 = clone.drinkMachine();

        NeedPushResult np5 = fur2.getCheckout().needsPush();
        assertSame(NO, np5);
        NeedPushResult np6 = drink2.getCheckout().needsPush();
        assertSame(NO, np6);

        assertFalse(fur2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");
        assertFalse(drink2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");
        fur2.getCheckout().fetchAll();
        drink2.getCheckout().fetchAll();
        assertTrue(fur2.getCheckout().needsPull(), "Needs pull not detected");
        assertTrue(drink2.getCheckout().needsPull(), "Needs pull not detected");

        fur2.runCactusTarget("pull", args(SCOPE, ALL_PROJECT_FAMILIES,
                FAMILIES, WOOKIES_FAMILY,
                INCLUDE_ROOT, true
        ));

        Path barbules2 = fur2.sourceFile("Barbules");
        assertTrue(exists(barbules2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + barbules1 + "\nclone should be in\n" + barbules2);

        Path sippyCup2 = drink2.sourceFile("SippyCup");
        assertTrue(exists(sippyCup2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + sippyCup1 + "\nclone should be in\n" + sippyCup2
                + " - if we got here, then some repositories were pushed, but not all."
        );
    }

    private void _testPushJustThisReallyPushesJustThis() throws Exception
    {
        StarWars clone = anotherClone();

        Path barbules1 = harness.run(Scenarios.CREATE_NEW_SOURCE_IN_FUR);
        Path sippyCup1 = harness.run(
                Scenarios.CREATE_NEW_SOURCE_IN_DRINK_MACHINE);
        ProjectWrapper fur = starwars.fur();
        fur.modifyPomFile();
        ProjectWrapper drink = starwars.drinkMachine();

        assertTrue(fur.getCheckout().isDirty(),
                "Repo status should be dirty");
        assertTrue(fur.getCheckout().hasUntrackedFiles(),
                "Repo should have untracked files");

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertTrue(drink.getCheckout().hasUntrackedFiles(),
                "Repo should not untracked files");

        String originalRootHead = starwars.getCheckout().head();

        fur.runCactusTarget("commit", args(
                SCOPE, JUST_THIS,
                PUSH, false,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));

        assertFalse(fur.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(fur.getCheckout().hasUntrackedFiles(),
                "Repo should not have untracked files");

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertTrue(drink.getCheckout().hasUntrackedFiles(),
                "After push scope=just-this on a related repo, "
                + "another should still have untracked files");

        String afterFirstCommitRootHead = starwars.getCheckout().head();
        assertNotEquals(originalRootHead, afterFirstCommitRootHead,
                "Should have generated a root commit, but the head has not changed.");

        drink.runCactusTarget("commit", args(
                SCOPE, JUST_THIS,
                PUSH, false,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));

        assertFalse(drink.getCheckout().isDirty(),
                "Repo status should not be dirty");
        assertFalse(drink.getCheckout().hasUntrackedFiles(),
                "Repo should no longer untracked files");

        String afterSecondCommitRootHead = starwars.getCheckout().head();
        assertNotEquals(afterFirstCommitRootHead, afterSecondCommitRootHead,
                "Should have generated a root commit, but the head has not changed.");

        NeedPushResult np1 = fur.getCheckout().needsPush();
        assertNotNull(np1);
        assertSame(YES, np1);
        NeedPushResult np2 = drink.getCheckout().needsPush();
        assertSame(YES, np2);

        NeedPushResult npRoot = starwars.getCheckout().needsPush();
        assertSame(YES, npRoot,
                "Root checkout should also need push.");

        boolean pushSucceeded = fur.runCactusTarget("push", args(
                SCOPE, Scope.FAMILY,
                INCLUDE_ROOT, true
        ));
        assertTrue(pushSucceeded, "Push failed");

        NeedPushResult np3 = fur.getCheckout().needsPush();
        assertSame(NO, np3);
        NeedPushResult np4 = drink.getCheckout().needsPush();
        assertSame(NO, np4);

        NeedPushResult npRoot2 = starwars.getCheckout().needsPush();

        assertSame(NO, npRoot2,
                "Root checkout was not pushed.");

        assertEquals(fur.getCheckout().head(),
                fur.getCheckout().remoteHead().get(),
                "Heads differ - push did not happen");

        assertEquals(drink.getCheckout().head(),
                drink.getCheckout().remoteHead().get(),
                "Heads differ - push did not happen");

        ProjectWrapper fur2 = clone.fur();
        ProjectWrapper drink2 = clone.drinkMachine();

        NeedPushResult np5 = fur2.getCheckout().needsPush();
        assertSame(NO, np5);
        NeedPushResult np6 = drink2.getCheckout().needsPush();
        assertSame(NO, np6);

        assertFalse(fur2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");
        assertFalse(drink2.getCheckout().needsPull(),
                "Needs pull should not be detected without an intervening fetch");

//        fur2.getCheckout().fetchAll();
//        drink2.getCheckout().fetchAll();
        fur2.getCheckout().updateRemoteHeads();
        drink2.getCheckout().updateRemoteHeads();

        assertTrue(fur2.getCheckout().needsPull(),
                "Needs pull not detected in " + fur2
                + " head " + fur2.getCheckout().head() + " remote head " + fur2
                .getCheckout().remoteHead().get());
        assertTrue(drink2.getCheckout().needsPull(),
                "Needs pull not detected in " + drink2
                + " head " + drink2.getCheckout().head() + " remote head " + drink2
                .getCheckout().remoteHead().get());

        String oldHead = fur2.getCheckout().head();
        String oldRemoteHead = fur2.getCheckout().remoteHead().get();
        boolean pullSucceeded = fur2.runCactusTarget("pull", args(
                SCOPE, Scope.FAMILY,
                FAMILIES, WOOKIES_FAMILY,
                INCLUDE_ROOT, true
        ));
        assertTrue(pullSucceeded, "pull failed");
        String newHead = fur2.getCheckout().head();
        String newRemoteHead = fur2.getCheckout().remoteHead().get();
        assertNotEquals(newHead, oldHead,
                "New remote head is same as old - pull picked up no changes or did not happen."
                + " Old remote head " + oldRemoteHead + " new remote head " + newRemoteHead);

        Path barbules2 = fur2.sourceFile("Barbules");
        assertTrue(exists(barbules2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + barbules1 + "\nclone should be in\n" + barbules2);

        Path sippyCup2 = drink2.sourceFile("SippyCup");
        assertTrue(exists(sippyCup2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + sippyCup1 + "\nclone should be in\n" + sippyCup2
                + " - if we got here, then some repositories were pushed, but not all."
        );
    }

    private void _testBranchesArePickedUp() throws Exception
    {
        StarWars clone = anotherClone();
        ProjectWrapper fur = starwars.fur();

        fur.runCactusTarget("checkout", args(SCOPE, FAMILY_OR_CHILD_FAMILY,
                CREATE_BRANCHES, true,
                CREATE_LOCAL_BRANCHES, true,
                BASE_BRANCH, "develop",
                TARGET_BRANCH, "features/stuff",
                INCLUDE_ROOT, true,
                "cactus.update-root", true,
                PUSH, false
        ));

        assertEquals("features/stuff", fur.currentBranch());

        String body = "package " + fur.javaPackage() + ";\npublic class Barbules {}\n";
        Path barbules1 = fur.newJavaSource("Barbules", body);
        assertTrue(exists(barbules1), "Was not created: " + barbules1);

        fur.modifyPomFile();

        ProjectWrapper drink = starwars.drinkMachine();
        assertEquals("features/stuff", drink.currentBranch());
        body = "package " + drink.javaPackage() + ";\npublic class SippyCup {}\n";
        Path sippyCup1 = drink.newJavaSource("SippyCup", body);
        assertTrue(exists(sippyCup1), "Was not created: " + sippyCup1);

        fur.runCactusTarget("commit", args(SCOPE, FAMILY_OR_CHILD_FAMILY,
                COMMIT_MESSAGE, "Some changes on a branch",
                INCLUDE_ROOT, false,
                PUSH, true
        ));

        assertFalse(fur.getCheckout().isDirty());
        assertFalse(fur.getCheckout().needsPush().canBePushed());
        assertFalse(drink.getCheckout().isDirty());
        assertFalse(drink.getCheckout().needsPush().canBePushed());

        String furHead = fur.getCheckout().head();
        String drinkHead = drink.getCheckout().head();

        fur.runCactusTarget("checkout", args(SCOPE, FAMILY_OR_CHILD_FAMILY,
                CREATE_BRANCHES, true,
                CREATE_LOCAL_BRANCHES, true,
                BASE_BRANCH, "develop",
                TARGET_BRANCH, "develop",
                INCLUDE_ROOT, false,
                "cactus.update-root", false,
                PUSH, false
        ));
        Thread.sleep(200);
        assertEquals("develop", fur.currentBranch());
        assertEquals("develop", drink.currentBranch());

        assertFalse(exists(barbules1),
                "Branch change should have removed file created on it");
        assertFalse(exists(sippyCup1),
                "Branch change should have removed file created on it");

        ProjectWrapper fur2 = clone.fur();
        ProjectWrapper drink2 = clone.drinkMachine();

        drink2.runCactusTarget("checkout", args(SCOPE, FAMILY_OR_CHILD_FAMILY,
                CREATE_BRANCHES, true,
                CREATE_LOCAL_BRANCHES, true,
                BASE_BRANCH, "develop",
                TARGET_BRANCH, "features/stuff",
                INCLUDE_ROOT, false,
                "cactus.update-root", false,
                PUSH, false
        ));

        assertEquals(furHead, fur2.getCheckout().head());
        assertEquals(drinkHead, drink2.getCheckout().head());

        Path barbules2 = fur2.sourceFile("Barbules");
        assertTrue(exists(barbules2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + barbules1 + "\nclone should be in\n" + barbules2);

        Path sippyCup2 = drink2.sourceFile("SippyCup");
        assertTrue(exists(sippyCup2),
                "Added and pushed source file was not pulled.  Original in "
                + "\n" + sippyCup1 + "\nclone should be in\n" + sippyCup2
                + " - if we got here, then some repositories were pushed, but not all."
        );

        drink2.runCactusTarget("merge", args(SCOPE, FAMILY_OR_CHILD_FAMILY,
                PUSH, true,
                "cactus.merge.from", "features/stuff",
                "cactus.merge.into", "develop"));

        assertEquals("develop", drink2.currentBranch());
        assertEquals("develop", fur2.currentBranch());
        assertTrue(exists(barbules2),
                "After branch merge, file should exist on target branch");
        assertTrue(exists(sippyCup2),
                "After branch merge, file should exist on target branch");

        drink.runCactusTarget("pull", args(
                SCOPE, Scope.FAMILY,
                FAMILIES, WOOKIES_FAMILY,
                INCLUDE_ROOT, true
        ));
        assertTrue(exists(barbules1),
                "After branch merge in second checkout and pull from first, file should exist on dev branch in first");
        assertTrue(exists(sippyCup1),
                "After branch merge in second checkout and pull from first, file should exist on dev branch in first");
    }

    private void _testCommitMojoWillNotPushIfConflict() throws IOException
    {
        StarWars clone = anotherClone();
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper fur2 = clone.fur();

        assertNotEquals(fur.path(), fur2.path(), "Got same checkout");

        fur.modifyPomFile();

        assertTrue(fur.getCheckout().isDirty());

        fur.runCactusTarget("commit", args(
                SCOPE, Scope.FAMILY,
                PUSH, true,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "This is some stuff"
        ));

        assertFalse(fur.getCheckout().isDirty(),
                "Repo status should not be dirty");

        assertEquals(fur.getCheckout().remoteHead().get(), fur.getCheckout()
                .head(), "Was not pushed");

        fur2.modifyPomFile("This is a different comment");
        fur2.modifySourceFile("Main");

        assertTrue(fur2.getCheckout().fetchAll());

        assertEquals(fur.getCheckout().remoteHead(), fur2.getCheckout()
                .remoteHead());

        assertNotEquals(fur2.getCheckout().head(), fur2.getCheckout()
                .remoteHead(),
                "Should have received the new remote head.");

        assertTrue(fur2.getCheckout().isDirty());

        boolean commitSucceeded = fur2.runCactusTarget("commit", args(
                SCOPE, Scope.FAMILY,
                PUSH, true,
                INCLUDE_ROOT, false,
                COMMIT_MESSAGE, "Pushing this will fail with a conflict"
        ));
        assertFalse(commitSucceeded, "Commit with push should have failed.");

        boolean commitWithoutPushSucceeded = fur2.runCactusTarget("commit",
                args(
                        SCOPE, Scope.FAMILY,
                        PUSH, false,
                        INCLUDE_ROOT, true,
                        COMMIT_MESSAGE,
                        "Pushing this would fail with a conflict"
                ));
        assertTrue(commitWithoutPushSucceeded,
                "Commit without push should succeed");

        boolean pullSucceeded = fur2.runCactusTarget("pull", args(SCOPE, ALL,
                INCLUDE_ROOT, true
        ));

        assertFalse(pullSucceeded);

        boolean pushSucceeded = fur2.runCactusTarget("push", args(SCOPE, ALL,
                INCLUDE_ROOT, true
        ));
        assertFalse(pushSucceeded, "Push should not succeed with conflicts");

        assertFalse(fur2.getCheckout().isDirty());
        assertNotEquals(fur2.getCheckout().head(), fur2.getCheckout()
                .remoteHead());
    }

    private void _testSimpleBumpProjectVersion() throws IOException
    {
        ProjectWrapper fur = starwars.fur();
        String oldHead = fur.getCheckout().head();
        assertTrue(fur.version().is("1.5.1"), fur.version().toString());
        boolean bumpSuccess = fur.runCactusTarget("bump-version",
                args(
                        SCOPE, Scope.FAMILY,
                        PUSH, true,
                        COMMIT_CHANGES, true,
                        INCLUDE_ROOT, true,
                        "cactus.create.release.branch", true,
                        "cactus.release.branch.prefix", "fnord",
                        "cactus.bump.published", false
                ));
        assertTrue(bumpSuccess);
        starwars.pomsChanged();

        ThrowingOptional<Pom> pom = starwars.poms().get(fur.artifactId());
        assertTrue(pom.isPresent());

        assertTrue(pom.get().version().is("1.5.2"), pom.get()::toString);
        assertNotEquals(oldHead, fur.getCheckout().head(),
                "A commit should have occurred.");

        assertEquals("fnord/1.5.2", fur.getCheckout().branch().get());

        Pom superpom = starwars.poms().get(
                ArtifactId.of("wookies-superpom")).get();

        assertTrue(superpom.version().is("2.0.1"),
                superpom::toString);

        Map<String, String> props = superpom.properties();
        assertNotNull(props);
        assertEquals("1.5.1", props.get("wookies.prev.version"));
        assertEquals("1.5.2", props.get("wookies.version"));

        Pom spaceships = starwars.poms().get(ArtifactId
                .of("spaceships-superpom")).get();

        assertTrue(spaceships.version().is("3.1.6"),
                "Version of spaceships superpom should have been updated via cascade");
        props = spaceships.properties();

        assertEquals("2.0.1", props.get("wookies-superpom.version"));
        assertEquals("3.1.6", props.get("spaceships.version"));

        Pom drinkMachine = starwars.poms().get(ArtifactId.of(
                "wookies-drink-machine")).get();
        assertTrue(drinkMachine.version().is("1.5.2"));
        assertTrue(drinkMachine.parent().isPresent());
        assertTrue(drinkMachine.parent().get().artifactId().is(
                "wookies-superpom"));
        assertTrue(drinkMachine.parent().get().version().is("2.0.1"));

        assertEquals("fnord/1.5.2", starwars.getCheckout().branch().get());
    }

    private void _testAutomergeTagsGeneratedByCommitMojo() throws IOException
    {
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drinkMachine = starwars.drinkMachine();
        ProjectWrapper deathStar = starwars.deathstar();
        ProjectWrapper superpoms = starwars.superpomsProject();

        Map<ProjectWrapper, String> oldHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);

        createBranches("stable", fur, drinkMachine, deathStar,
                superpoms, starwars);
        createBranches("feature/thingamabob", fur, drinkMachine,
                deathStar, superpoms, starwars);

        modifySources(fur, drinkMachine, deathStar);

        assertDirty(fur, drinkMachine, deathStar);

        fur.runCactusTarget("commit", args(
                SCOPE, ALL_PROJECT_FAMILIES,
                CREATE_AUTOMERGE_TAG, true,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "Doing some stuff",
                PUSH, true
        ));

        Map<ProjectWrapper, String> newHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);
        assertNotEquals(oldHeads.get(fur), newHeads.get(fur),
                "Commit did not happen in " + fur);
        assertNotEquals(oldHeads.get(drinkMachine), newHeads.get(drinkMachine),
                "Commit did not happen in " + drinkMachine);
        assertNotEquals(oldHeads.get(deathStar), newHeads.get(deathStar),
                "Commit did not happen in " + deathStar);
        assertNotEquals(oldHeads.get(starwars), newHeads.get(starwars),
                "Commit did not happen in root checkout, but should have");

        assertEquals(oldHeads.get(superpoms), newHeads.get(superpoms));

        Map<ProjectWrapper, String> localTags = findAutomergeTags(
                fur, drinkMachine, deathStar, superpoms, starwars);

        assertFalse(localTags.isEmpty(),
                "Did not find any automerge tags locally");
        assertTrue(localTags.containsKey(fur));
        assertTrue(localTags.containsKey(drinkMachine));
        assertTrue(localTags.containsKey(deathStar));
        assertTrue(localTags.containsKey(starwars));
        assertFalse(localTags.containsKey(superpoms), "Superpoms project was "
                + "up-to-date with stable branch - it should not have been tagged");

        StarWars clone = anotherClone();
        assertNotSame(starwars, clone);
        ProjectWrapper rfur = clone.fur();
        assertNotSame(rfur, fur);
        ProjectWrapper rdrinkMachine = clone.drinkMachine();
        assertNotSame(rdrinkMachine, drinkMachine);
        ProjectWrapper rdeathStar = clone.deathstar();
        assertNotSame(rdeathStar, deathStar);
        ProjectWrapper rsuperpoms = clone.superpomsProject();
        assertNotSame(rsuperpoms, superpoms);

        Map<ProjectWrapper, String> remoteTags
                = findAutomergeTags(
                        rfur, rdrinkMachine, rdeathStar,
                        rsuperpoms, clone);

        assertEquals(localTags.get(fur),
                remoteTags.get(rfur), "Wrong or missing tag for fur");
        assertEquals(localTags.get(drinkMachine), remoteTags.get(rdrinkMachine),
                "Wrong or missing tag for drink-machine");
        assertEquals(localTags.get(deathStar),
                remoteTags.get(rdeathStar),
                "Wrong or missing tag for deathstar");
        assertEquals(localTags.get(starwars), remoteTags.get(clone),
                "Wrong or missing tag for root");
    }

    private void _testAutomergeTagsGeneratedByAutoMergeTagMojoWithAutopush()
            throws IOException
    {
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drinkMachine = starwars.drinkMachine();
        ProjectWrapper deathStar = starwars.deathstar();
        ProjectWrapper superpoms = starwars.superpomsProject();

        Map<ProjectWrapper, String> oldHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);

        createBranches("stable", fur, drinkMachine, deathStar,
                superpoms, starwars);
        createBranches("feature/thingamabob", fur, drinkMachine,
                deathStar, superpoms, starwars);

        modifySources(fur, drinkMachine, deathStar);

        assertDirty(fur, drinkMachine, deathStar);

        boolean committed = fur.runCactusTarget("commit", args(
                SCOPE, ALL_PROJECT_FAMILIES,
                CREATE_AUTOMERGE_TAG, true,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "Doing some more stuff",
                PUSH, false
        ));

        assertTrue(committed, "Commit failed");

        Map<ProjectWrapper, String> newHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);
        assertNotEquals(oldHeads.get(fur), newHeads.get(fur),
                "Commit did not happen in " + fur);
        assertNotEquals(oldHeads.get(drinkMachine), newHeads.get(drinkMachine),
                "Commit did not happen in " + drinkMachine);
        assertNotEquals(oldHeads.get(deathStar), newHeads.get(deathStar),
                "Commit did not happen in " + deathStar);
        assertNotEquals(oldHeads.get(starwars), newHeads.get(starwars),
                "Commit did not happen in root checkout, but should have");

        assertEquals(oldHeads.get(superpoms), newHeads.get(superpoms));

        fur.runCactusTarget("automerge-tag", args(
                SCOPE, ALL_PROJECT_FAMILIES,
                PUSH, true
        ));

        Map<ProjectWrapper, String> localTags = findAutomergeTags(
                fur, drinkMachine, deathStar, superpoms, starwars);

        assertFalse(localTags.isEmpty(),
                "Did not find any automerge tags locally");
        assertTrue(localTags.containsKey(fur));
        assertTrue(localTags.containsKey(drinkMachine));
        assertTrue(localTags.containsKey(deathStar));
        assertTrue(localTags.containsKey(starwars));
        assertFalse(localTags.containsKey(superpoms), "Superpoms project was "
                + "up-to-date with stable branch - it should not have been tagged");

        StarWars clone = anotherClone();
        assertNotSame(starwars, clone);
        ProjectWrapper rfur = clone.fur();
        assertNotSame(rfur, fur);
        ProjectWrapper rdrinkMachine = clone.drinkMachine();
        assertNotSame(rdrinkMachine, drinkMachine);
        ProjectWrapper rdeathStar = clone.deathstar();
        assertNotSame(rdeathStar, deathStar);
        ProjectWrapper rsuperpoms = clone.superpomsProject();
        assertNotSame(rsuperpoms, superpoms);

        Map<ProjectWrapper, String> remoteTags
                = findAutomergeTags(
                        rfur, rdrinkMachine, rdeathStar,
                        rsuperpoms, clone);

        assertEquals(localTags.get(fur),
                remoteTags.get(rfur), "Wrong or missing tag for fur");
        assertEquals(localTags.get(drinkMachine), remoteTags.get(rdrinkMachine),
                "Wrong or missing tag for drink-machine");
        assertEquals(localTags.get(deathStar),
                remoteTags.get(rdeathStar),
                "Wrong or missing tag for deathstar");
        assertEquals(localTags.get(starwars), remoteTags.get(clone),
                "Wrong or missing tag for root");
    }

    private void _testAutomergeTagsGeneratedByAutoMergeTagMojoWithExplictPush()
            throws IOException
    {
        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drinkMachine = starwars.drinkMachine();
        ProjectWrapper deathStar = starwars.deathstar();
        ProjectWrapper superpoms = starwars.superpomsProject();

        Map<ProjectWrapper, String> oldHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);

        createBranches("stable", fur, drinkMachine, deathStar,
                superpoms, starwars);
        createBranches("feature/thingamabob", fur, drinkMachine,
                deathStar, superpoms, starwars);

        modifySources(fur, drinkMachine, deathStar);

        assertDirty(fur, drinkMachine, deathStar);

        boolean committed = fur.runCactusTarget("commit", args(
                SCOPE, ALL_PROJECT_FAMILIES,
                CREATE_AUTOMERGE_TAG, false,
                INCLUDE_ROOT, true,
                COMMIT_MESSAGE, "Doing some more stuff",
                PUSH, false
        ));

        assertTrue(committed, "Commit failed");

        Map<ProjectWrapper, String> newHeads = heads(starwars, fur,
                drinkMachine, deathStar, superpoms);
        assertNotEquals(oldHeads.get(fur), newHeads.get(fur),
                "Commit did not happen in " + fur);
        assertNotEquals(oldHeads.get(drinkMachine), newHeads.get(drinkMachine),
                "Commit did not happen in " + drinkMachine);
        assertNotEquals(oldHeads.get(deathStar), newHeads.get(deathStar),
                "Commit did not happen in " + deathStar);
        assertNotEquals(oldHeads.get(starwars), newHeads.get(starwars),
                "Commit did not happen in root checkout, but should have");

        assertEquals(oldHeads.get(superpoms), newHeads.get(superpoms));

        fur.runCactusTarget("push", args(
                SCOPE, ALL_PROJECT_FAMILIES,
                CREATE_AUTOMERGE_TAG, true
        ));

        Map<ProjectWrapper, String> localTags = findAutomergeTags(
                fur, drinkMachine, deathStar, superpoms, starwars);

        assertFalse(localTags.isEmpty(),
                "Did not find any automerge tags locally");
        assertTrue(localTags.containsKey(fur));
        assertTrue(localTags.containsKey(drinkMachine));
        assertTrue(localTags.containsKey(deathStar));
        assertFalse(localTags.containsKey(starwars),
                "PushMojo does not update the root");
        assertFalse(localTags.containsKey(superpoms), "Superpoms project was "
                + "up-to-date with stable branch - it should not have been tagged");

        StarWars clone = anotherClone();
        assertNotSame(starwars, clone);
        ProjectWrapper rfur = clone.fur();
        assertNotSame(rfur, fur);
        ProjectWrapper rdrinkMachine = clone.drinkMachine();
        assertNotSame(rdrinkMachine, drinkMachine);
        ProjectWrapper rdeathStar = clone.deathstar();
        assertNotSame(rdeathStar, deathStar);
        ProjectWrapper rsuperpoms = clone.superpomsProject();
        assertNotSame(rsuperpoms, superpoms);

        Map<ProjectWrapper, String> remoteTags
                = findAutomergeTags(
                        rfur, rdrinkMachine, rdeathStar,
                        rsuperpoms, clone);

        assertEquals(localTags.get(fur),
                remoteTags.get(rfur), "Wrong or missing tag for fur");
        assertEquals(localTags.get(drinkMachine), remoteTags.get(rdrinkMachine),
                "Wrong or missing tag for drink-machine");
        assertEquals(localTags.get(deathStar),
                remoteTags.get(rdeathStar),
                "Wrong or missing tag for deathstar");
        assertFalse(remoteTags.containsKey(clone));
    }

    private StarWars anotherClone() throws IOException
    {
        return starwars.newClone();
    }

    @BeforeEach
    public void setup(TestInfo info) throws IOException
    {
        harness = new StarWarsHarness(info);
        starwars = harness.starwars();
    }

    @AfterEach
    public void cleanup(TestInfo info) throws IOException
    {
        if (harness != null)
        {
            harness.teardown();
        }
    }
}
