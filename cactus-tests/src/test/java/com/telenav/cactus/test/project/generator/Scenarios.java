package com.telenav.cactus.test.project.generator;

import com.mastfrog.function.throwing.ThrowingBiConsumer;
import com.mastfrog.function.throwing.ThrowingConsumer;
import com.mastfrog.function.throwing.ThrowingFunction;
import com.mastfrog.function.throwing.ThrowingRunnable;
import com.mastfrog.function.throwing.ThrowingTriConsumer;
import com.telenav.cactus.git.Branches.Branch;
import com.telenav.cactus.git.GitCheckout;
import com.telenav.cactus.git.GitCommand;
import com.telenav.cactus.test.project.ProjectWrapper;
import com.telenav.cactus.test.project.starwars.StarWars;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.telenav.cactus.cli.ProcessResultConverter.exitCodeIsZero;
import static java.nio.file.Files.exists;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 * @author Tim Boudreau
 */
public final class Scenarios
{

    public static final ThrowingConsumer<StarWars> FETCH_ALL_AND_PRUNE = sw ->
    {
        sw.checkouts().forEach(GitCheckout::updateRemoteHeads);
        sw.checkouts().forEach(GitCheckout::fetchAll);
        sw.checkouts().forEach(
                GitCheckout::fetchPruningDefunctLocalRecordsOfRemoteBranches);
    };

    public static final ThrowingConsumer<StarWars> FETCH_ALL_AND_PRUNE_AND_PULL = sw ->
    {
        FETCH_ALL_AND_PRUNE.accept(sw);
        sw.checkouts().forEach(GitCheckout::pull);
    };

    public static final ThrowingBiConsumer<String, StarWars> SWITCH_BRANCH = (branch, sw) ->
    {
        sw.checkouts().forEach(co ->
        {
            assertTrue(co.switchToBranch(branch), "Could not switch "
                    + co.loggingName() + " to branch '" + branch + "'");
        });
    };

    public static final ThrowingTriConsumer<String, String, StarWars> MERGE_BRANCH_TO = (from, to, starwars) ->
    {
        starwars.checkouts().forEach(co ->
        {
            co.switchToBranch(to);
            co.merge(from);
        });
    };

    public static final ThrowingTriConsumer<String, String, StarWars> MERGE_BRANCH_TO_AND_PUSH = (from, to, starwars) ->
    {
        starwars.checkouts().forEach(co ->
        {
            co.switchToBranch(to);
            co.merge(from);
            co.push();
        });
    };

    public static final ThrowingTriConsumer<String, ThrowingRunnable, StarWars> CREATE_BRANCH_IN_ALL_AND_RUN = (branch, run, starwars) ->
    {
        Map<GitCheckout, Branch> originalBranchForCheckout = new LinkedHashMap<>();

        Consumer<GitCheckout> ch = checkout ->
        {
            checkout.branches().currentBranch().ifPresent(
                    originalBranch ->
            {
                originalBranchForCheckout.put(checkout, originalBranch);
                boolean branched = checkout.createAndSwitchToBranch(branch,
                        Optional.of(
                                originalBranch.name()));
                System.out.println("Create branch " + branch + " in " + checkout
                        .loggingName());
                assertTrue(branched,
                        "Create branch " + branch + " in " + checkout
                                .loggingName());
            });
        };

        starwars.checkouts().forEach(ch);

        run.run();

        originalBranchForCheckout.forEach((checkout, origBranch) ->
        {
            GitCommand<Boolean> gc = new GitCommand<>(exitCodeIsZero(), checkout
                    .checkoutRoot(),
                    "push", "-u", "origin", branch);
            System.out.println("Push branch " + branch + " in " + checkout
                    .loggingName());
            boolean pushed = gc.run().awaitQuietly();
            assertTrue(pushed, "Push failed on " + checkout.checkoutRoot());
        });

        originalBranchForCheckout.forEach((checkout, origBranch) ->
        {
            Branch br = originalBranchForCheckout.get(checkout);
            assertNotNull(br);
            boolean switched = checkout.switchToBranch(origBranch.name());
            assertTrue(switched,
                    "Fail to switch back to original branch " + origBranch + " in "
                    + checkout.loggingName());
        });
    };

    public static final ThrowingBiConsumer<String, StarWars> CREATE_BRANCH_IN_ALL_NO_PUSH = (branch, starwars) ->
    {
        Map<GitCheckout, Branch> originalBranchForCheckout = new LinkedHashMap<>();
        GitCheckout root = starwars.getCheckout();

        Consumer<GitCheckout> ch = checkout ->
        {
            checkout.branches().currentBranch().ifPresent(
                    originalBranch ->
            {
                originalBranchForCheckout.put(checkout, originalBranch);
                boolean branched = checkout.createAndSwitchToBranch(branch,
                        Optional.of(
                                originalBranch.name()));
                assertTrue(branched,
                        "Create branch " + branch + " in " + checkout
                                .loggingName());
            });
        };

        root.submodules().ifPresent(subs
                -> subs.forEach(sub
                        -> sub.checkout().ifPresent(ch)));
        ch.accept(root);

        originalBranchForCheckout.forEach((checkout, origBranch) ->
        {
            Branch br = originalBranchForCheckout.get(checkout);
            assertNotNull(br);
            checkout.switchToBranch(origBranch.name());
        });
    };

    public static final ThrowingTriConsumer<String, ThrowingRunnable, StarWars> CREATE_BRANCH_IN_ALL_AND_RUN_NO_PUSH = (branch, run, starwars) ->
    {
        Map<GitCheckout, Branch> originalBranchForCheckout = new LinkedHashMap<>();
        GitCheckout root = starwars.getCheckout();

        Consumer<GitCheckout> ch = checkout ->
        {
            checkout.branches().currentBranch().ifPresent(
                    originalBranch ->
            {
                originalBranchForCheckout.put(checkout, originalBranch);
                boolean branched = checkout.createAndSwitchToBranch(branch,
                        Optional.of(
                                originalBranch.name()));
                System.out.println(
                        "Nopush: Create branch " + branch + " in " + checkout
                                .loggingName());
                assertTrue(branched,
                        "Create branch " + branch + " in " + checkout
                                .loggingName());

            });
        };

        root.submodules().ifPresent(subs
                -> subs.forEach(sub
                        -> sub.checkout().ifPresent(ch)));
        ch.accept(root);

        run.run();

        originalBranchForCheckout.forEach((checkout, origBranch) ->
        {
            Branch br = originalBranchForCheckout.get(checkout);
            assertNotNull(br);
            checkout.switchToBranch(origBranch.name());
        });
    };

    public static final ThrowingBiConsumer<String, StarWars> CREATE_BRANCH_IN_ALL = (branch, starwars) ->
    {
        CREATE_BRANCH_IN_ALL_AND_RUN.accept(
                branch,
                () ->
        {
        },
                starwars);
    };

    public static final ThrowingFunction<StarWars, Path> CREATE_NEW_SOURCE_IN_FUR
            = starwars ->
    {
        ProjectWrapper fur = starwars.fur();
        String body = "package " + fur.javaPackage() + ";\npublic class Barbules {}\n";
        Path barbules1 = fur.newJavaSource("Barbules", body);
        assertTrue(exists(barbules1), "Was not created: " + barbules1);
        return barbules1;
    };

    public static final ThrowingConsumer<StarWars> MODIFY_POM_IN_DEATHSTAR
            = starwars ->
    {
        ProjectWrapper unrelatedProject = starwars.deathstar();
        assertTrue(unrelatedProject.modifyPomFile());
        assertTrue(unrelatedProject.getCheckout().isDirty(),
                "Project should be dirty");
    };

    public static final ThrowingFunction<StarWars, Path> CREATE_NEW_SOURCE_IN_DRINK_MACHINE
            = starwars ->
    {
        ProjectWrapper drink = starwars.drinkMachine();
        String body = "package " + drink.javaPackage() + ";\npublic class SippyCup {}\n";
        Path sippyCup1 = drink.newJavaSource("SippyCup", body);
        assertTrue(exists(sippyCup1), "Was not created: " + sippyCup1);
        return sippyCup1;
    };

}
