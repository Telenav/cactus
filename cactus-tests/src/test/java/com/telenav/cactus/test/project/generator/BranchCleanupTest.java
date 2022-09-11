package com.telenav.cactus.test.project.generator;

import com.telenav.cactus.git.GitCommand;
import com.telenav.cactus.scope.Scope;
import com.telenav.cactus.test.project.ProjectWrapper;
import com.telenav.cactus.test.project.starwars.StarWars;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static com.telenav.cactus.cli.ProcessResultConverter.strings;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.CREATE_AUTOMERGE_TAG;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.INCLUDE_ROOT;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.PUSH;
import static com.telenav.cactus.maven.common.CactusCommonPropertyNames.SCOPE;
import static com.telenav.cactus.test.project.generator.Scenarios.*;
import static com.telenav.cactus.test.project.generator.StarWarsHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the branch cleanup mojo will not delete branches that contain
 * commits that have not been pushed to a "safe" branch.
 *
 * @author Tim Boudreau
 */
public class BranchCleanupTest
{
    private StarWarsHarness harness;
    private StarWars starwars;

    @Test
    public void testBranchCleanup() throws Exception
    {
        harness.runTest(false, this::_testBranchCleanup);
    }

    private void _testBranchCleanup() throws Exception
    {
        StarWarsHarness clonedHarness = harness.cloneHarness();

        ProjectWrapper fur = starwars.fur();
        ProjectWrapper drinkMachine = starwars.drinkMachine();
        ProjectWrapper exhaustPort = starwars.exhaustPort();
        ProjectWrapper slugFarm = starwars.slugfarm();

        CREATE_BRANCH_IN_ALL.accept("release/current", starwars);

        assertBranchExists("release/current", true, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);
        assertBranchExists("release/current", false, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);

        CREATE_BRANCH_IN_ALL_AND_RUN.accept("feature/modified", () ->
        {
            fur.modifyPomFile();
            assertTrue(fur.commit("Merge stuff into fur"),
                    "Failed to commit in fur");
            drinkMachine.modifySourceFile("Main");
            assertTrue(drinkMachine.commit("Merge stuff into drink machine"),
                    "Failed to commit in drink-machine");
            exhaustPort.modifyPomFile("Glug glug");
            assertTrue(exhaustPort.commit("Merge stuff into exhaust port"),
                    "Failed to commit in exhaust-port");
        }, starwars);

        assertBranchExists("feature/modified", true, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);
        assertBranchExists("feature/modified", false, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);

        CREATE_BRANCH_IN_ALL_AND_RUN.accept("feature/already-merged", () ->
        {
            fur.addDependency("com.mastfrog", "util-strings", "2.8.3");
            drinkMachine.addDependency("com.mastfrog", "util-strings", "2.8.3");
            exhaustPort.addDependency("com.mastfrog", "util-strings", "2.8.3");
            slugFarm.addDependency("com.mastfrog", "util-strings", "2.8.3");

            assertTrue(fur.commit("AM commit in fur"), "Failed to commit fur");
            assertTrue(drinkMachine.commit("AM commit in drink-machine"),
                    "Failed to commit drink-machine");
            assertTrue(exhaustPort.commit("AM commit in exaust-port"),
                    "Failed to commit exhaust-port");
            assertTrue(slugFarm.commit("AM commit in slug-farm"),
                    "Failed to commit slug-farm");

            assertTrue(fur.push(), "Failed to push fur");
            assertTrue(drinkMachine.push(), "Failed to push drink-machine");
            assertTrue(exhaustPort.push(), "Failed to push exhaust-port");
            assertTrue(slugFarm.push(), "Failed to push slug-farm");

        }, starwars);

        MERGE_BRANCH_TO_AND_PUSH.accept("feature/already-merged", "develop",
                starwars);

        assertBranchExists("feature/already-merged", true, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);
        assertBranchExists("feature/already-merged", false, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);

        CREATE_BRANCH_IN_ALL_NO_PUSH.accept("someRandomBranch", starwars);

        CREATE_BRANCH_IN_ALL_AND_RUN_NO_PUSH.accept(
                "someRandomBranchWithCommits", () ->
        {
            assertEquals("someRandomBranchWithCommits",
                    fur.currentBranch(),
                    "On wrong branch in fur.");
            assertEquals("someRandomBranchWithCommits",
                    slugFarm.currentBranch(),
                    "On wrong branch in slug-farm.");
            assertEquals("someRandomBranchWithCommits",
                    exhaustPort.currentBranch(),
                    "On wrong branch in exhaust-port.");
            fur.modifyPomFile("Foo foo fru");
            assertTrue(fur.commit("srb commit in fur"), "Failed to commit fur");
            slugFarm.modifyPomFile("Wurg burg");
            assertTrue(slugFarm.commit("srb commit in slug-farm"),
                    "Failed to commit in slug-farm");
            exhaustPort.modifyPomFile("Glark bark");
            assertTrue(exhaustPort.commit("srb commit in exhaust-prot"),
                    "Failed to commit in exhuast-port");
        }, starwars);

        assertBranchExists("someRandomBranch", true, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);
        assertBranchExists("someRandomBranchWithCommits", true, fur,
                drinkMachine, exhaustPort, slugFarm, starwars);

        assertBranchDoesNotExist("someRandomBranch", false, fur, drinkMachine,
                exhaustPort, slugFarm, starwars);
        assertBranchDoesNotExist("someRandomBranchWithCommits", false, fur,
                drinkMachine, exhaustPort, slugFarm, starwars);

        assertEquals("develop", fur.getCheckout().branch().get());
        assertEquals("develop", exhaustPort.getCheckout().branch().get());
        assertEquals("develop", slugFarm.getCheckout().branch().get());
        assertEquals("develop", drinkMachine.getCheckout().branch().get());
        assertEquals("develop", starwars.getCheckout().branch().get());

        boolean cleanedUp = slugFarm.runCactusTarget("remote-branch-cleanup",
                args(
                        SCOPE, Scope.ALL_PROJECT_FAMILIES,
                        "cactus.i-understand-the-risks", true,
                        "cactus.cleanup-remote", true,
                        "cactus.cleanup-local", true
                ));
        assertTrue(cleanedUp, "Cleanup mojo failed");

        clonedHarness.run(FETCH_ALL_AND_PRUNE);

        StarWars clone = clonedHarness.starwars();

        ProjectWrapper cfur = clone.fur();
        ProjectWrapper cdrinkMachine = clone.drinkMachine();
        ProjectWrapper cexhaustPort = clone.exhaustPort();
        ProjectWrapper cslugFarm = clone.slugfarm();

        // None of these branches will exist locally in the clone since
        // they have never been checked out
        //
        // This branch has commits that have not been merged with a safe branch,
        // so this branch should still exist
        assertBranchExists(
                "feature/modified",
                false,
                cfur,
                cdrinkMachine,
                cexhaustPort,
                cslugFarm);

        // This branch was fully merged, so it should have been completely cleaned up
        assertBranchDoesNotExist(
                "feature/already-merged",
                false,
                cfur,
                cdrinkMachine,
                cexhaustPort,
                cslugFarm);

        // This branch was never pushed, so it should not show up for any clone
        assertBranchDoesNotExist(
                "someRandomBranch",
                false,
                cfur,
                cdrinkMachine,
                cexhaustPort,
                cslugFarm);

        // This branch was never pushed and has commits that have not been merged
        // to a safe branch, so it should remain locally
        assertBranchExists(
                "someRandomBranchWithCommits",
                true,
                fur,
                exhaustPort,
                slugFarm);

        // This branch was never pushed and contains no commits that are not on a
        // safe branch, so this local branch should have been deleted
        assertBranchDoesNotExist(
                "someRandomBranch",
                true,
                fur,
                drinkMachine,
                exhaustPort, slugFarm);

        String furHead = fur.getCheckout().headOf(
                "feature/modified");
        assertNotNull(furHead);
        String drinkMachineHead = drinkMachine.getCheckout().headOf(
                "feature/modified");
        assertNotNull(drinkMachineHead);
        String exhaustPortHead = exhaustPort.getCheckout().headOf(
                "feature/modified");
        assertNotNull(exhaustPortHead);

        // And throw in a test of the merge mojo, since we have a branch
        // with content ready to merge.
        boolean mergeSuccess = fur.runCactusTarget("merge", args(
                SCOPE, Scope.ALL_PROJECT_FAMILIES,
                "cactus.merge.from", "feature/modified",
                "cactus.also.merge.into", "release/current",
                "cactus.delete.merged.branch", true,
                INCLUDE_ROOT, true,
                PUSH, false
        ));
        assertTrue(mergeSuccess, "Merge branch mojo failed");

        boolean pushSuccess = fur.runCactusTarget("push", args(
                SCOPE, Scope.ALL_PROJECT_FAMILIES,
                "cactus.push.all", true,
                CREATE_AUTOMERGE_TAG, true,
                INCLUDE_ROOT, true
        ));
        assertTrue(pushSuccess, "Push all failed");

        String log = new GitCommand<>(strings(), fur.getCheckout()
                .checkoutRoot(), "log", "--no-color", "-n", "10")
                .run().awaitQuietly();
        System.out.println("\n\n---------- the log -----------");
        System.out.println(log);
        System.out.println("\n\n---------- the log -----------");

        assertTag("modified", fur, drinkMachine, exhaustPort);

        assertBranchDoesNotExist("feature/modified", true, fur);
        assertBranchDoesNotExist("feature/modified", true, drinkMachine);
        assertBranchDoesNotExist("feature/modified", true, exhaustPort);

        harness.run("develop", Scenarios.SWITCH_BRANCH);

        clonedHarness.run(FETCH_ALL_AND_PRUNE_AND_PULL);
        harness.run(FETCH_ALL_AND_PRUNE_AND_PULL);
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
