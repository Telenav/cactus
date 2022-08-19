package com.telenav.cactus.cli;

import com.mastfrog.concurrent.future.AwaitableCompletionStage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static com.telenav.cactus.cli.ProcessResultConverter.exitCodeIsZero;
import static java.lang.System.currentTimeMillis;
import static java.util.Optional.of;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class CliCommandTest
{

    @Test
    public void testCliCommandsThatCannotBeLaunchedAreNotWaitedOnIndefinitely()
            throws InterruptedException
    {
        Duration wait = Duration.ofMinutes(1);
        CliCommand<String> cli = CliCommand.fixed("wugwugwugwugwug", Paths.get(
                "."), "hello");
        AwaitableCompletionStage<String> stage = cli.run();
        System.out.println("STAGE IS " + stage + " for " + cli);
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Throwable> thrownRef = new AtomicReference<>();
        stage.whenComplete((str, thrown) ->
        {
            thrownRef.set(thrown);
            called.set(true);
            System.out.println("COMPLETED " + str + " " + thrown);
        });
        long then = currentTimeMillis();
        String result = stage.await(wait);
        long elapsed = currentTimeMillis() - then;

        System.out.println(
                "Waited " + elapsed + " called " + called.get() + " result "
                + result);

        assertNull(result, "Result should be null");
        assertTrue(elapsed < wait.toMillis(),
                "Should not have waited to full timeout, but waited for "
                + elapsed + "ms");
        assertTrue(called.get(), "Callback was never invoked");
        assertNotNull(thrownRef.get());
        assertTrue(thrownRef.get() instanceof IOException);
    }

    @Test
    public void testCliCommandsThatCannotBeLaunchedAreNotWaitedOnIndefinitelyWithBooleanResult()
            throws InterruptedException
    {
        Duration wait = Duration.ofMinutes(1);
        NonExistentBooleanCliCommand cli = new NonExistentBooleanCliCommand();
        AwaitableCompletionStage<Boolean> stage = cli.run();
        System.out.println("STAGE IS " + stage + " for " + cli);
        AtomicBoolean called = new AtomicBoolean();
        AtomicReference<Throwable> thrownRef = new AtomicReference<>();
        stage.whenComplete((str, thrown) ->
        {
            thrownRef.set(thrown);
            called.set(true);
            System.out.println("COMPLETED " + str + " " + thrown);
        });
        long then = currentTimeMillis();
        Boolean result = stage.await(wait);
        long elapsed = currentTimeMillis() - then;

        System.out.println(
                "Waited " + elapsed + " called " + called.get() + " result "
                + result);

        assertNull(result, "Result should be null");
        assertTrue(elapsed < wait.toMillis(),
                "Should not have waited to full timeout, but waited for "
                + elapsed + "ms");
        assertTrue(called.get(), "Callback was never invoked");
        assertTrue(cli.configureArgsCalled, "Arguments were never configured");
        assertNotNull(thrownRef.get());
        assertTrue(thrownRef.get() instanceof IOException);
    }

    private static class NonExistentBooleanCliCommand extends CliCommand<Boolean>
    {
        volatile boolean configureArgsCalled;

        NonExistentBooleanCliCommand()
        {
            super("glugwugwugwugwugwug", exitCodeIsZero());
        }

        @Override
        protected void configureArguments(List<String> list)
        {
            configureArgsCalled = true;
            list.add("Hello");
        }

        @Override
        protected Optional<Path> workingDirectory()
        {
            return of(Paths.get("."));
        }
    }

}
