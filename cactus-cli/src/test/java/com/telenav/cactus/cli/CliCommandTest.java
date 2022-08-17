package com.telenav.cactus.cli;

import com.telenav.cactus.cli.nuprocess.ProcessControl;
import com.telenav.cactus.cli.nuprocess.ProcessResult;
import com.telenav.cactus.cli.nuprocess.ProcessState;
import com.telenav.cactus.cli.nuprocess.internal.ProcessCallback;
import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.NuProcessHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static com.telenav.cactus.cli.ProcessResultConverter.strings;
import static java.lang.Math.abs;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Tim Boudreau
 */
public class CliCommandTest
{

    private static Path file;
    private static Path inputFile;

    @Test
    public void testNuprocessWorksAtAll() throws Exception
    {
        assertTrue(true);
        NuProcessBuilder bldr = new NuProcessBuilder(file.toString(),
                "Test Exec");
//        NuProcessBuilder bldr = new NuProcessBuilder("/bin/ls",
//                "-la", ".");
        H h = new H();
        bldr.setProcessListener(h);
        bldr.start();
        Thread.sleep(2000);
    }

    @Test
    public void testCliCommand() throws Exception
    {
        CliCommand<String> cli = new Cli<>(file.toString(), strings(),
                "The Test");

        String output = cli.run().awaitQuietly();
        assertEquals("hello The Test\n"
                + "goodbye The Test\n", output);
    }

    @Test
    public void testKilledExits() throws Exception
    {
        NuProcessBuilder npb = new NuProcessBuilder("/bin/sleep", "10");

        ProcessCallback cb = new ProcessCallback();
        npb.setProcessListener(cb);

        for (int i = 0; i < 7 || !cb.state().isRunning(); i++)
        {
            Thread.sleep(200);
            if (i == 1)
            {
                npb.start();
            }
        }

        AtomicReference<ProcessState> killState = new AtomicReference<>();
        cb.listen((st, out, err) ->
        {
            killState.set(st);
        });

        assertTrue(cb.state().isRunning());

        boolean killed = cb.kill();
        assertTrue(killed, "Not killed: " + cb.state());

        cb.await(Duration.ofSeconds(15));

        ProcessState finalState = cb.state();
        assertTrue(finalState.isExited());
        assertFalse(finalState.isRunning());
        assertNotEquals(0, finalState.exitCode());

        assertNotNull(killState.get(),
                "Listener not notified of exit.  State: " + cb.state());

        assertEquals(finalState, killState.get());
    }

    @Test
    public void testInput() throws Exception
    {
        NuProcessBuilder bldr = new NuProcessBuilder(inputFile.toString());
        ProcessControl cb = ProcessControl.create(bldr);
        cb.withStdinHandler((proc, in) ->
        {
            if (in.remaining() == 0)
            {
                return true;
            }
            in.put("Well hello there.  This is some output.\n".getBytes(UTF_8));
            in.flip();
            return false;
        }, true);
        NuProcess proc = bldr.start();
        assertNotNull(proc);
        cb.await(Duration.ofMinutes(1));

        ProcessResult res = cb.result();
        assertTrue(res.hasExited());
        assertFalse(res.wasKilled());
        assertTrue(res.isSuccess());

        assertTrue(res.stdout.contains("Your input was\n"
                + "Well hello there. This is some output.\n"));
    }

    @Test
    public void testInputAbort() throws Exception
    {
        AtomicBoolean aborted = new AtomicBoolean();
        NuProcessBuilder bldr = new NuProcessBuilder(inputFile.toString());
        ProcessControl cb = ProcessControl.create(bldr);
        cb.abortOnInput(() ->
        {
            aborted.set(true);
        });
        NuProcess proc = bldr.start();
        cb.await(Duration.ofMinutes(1));

        proc.waitFor(1, TimeUnit.MINUTES);
        ProcessResult res = cb.result();
        assertTrue(aborted.get());
        assertTrue(res.wasKilled(), "Process should have been killed");
        assertFalse(res.isSuccess());
    }

    static class Cli<T> extends CliCommand<T>
    {
        private final String[] args;

        public Cli(String name, ProcessResultConverter<T> resultCreator,
                String... args)
        {
            super(name, resultCreator);
            this.args = args;
        }

        @Override
        protected void configureArguments(List<String> list)
        {
            list.addAll(asList(args));
        }

        @Override
        protected Optional<Path> workingDirectory()
        {
            return Optional.of(file.getParent());
        }
    }

    static class H implements NuProcessHandler
    {
        private NuProcess np;
        private StringBuilder stdout = new StringBuilder();
        private StringBuilder stderr = new StringBuilder();

        @Override
        public void onPreStart(NuProcess np)
        {
        }

        @Override
        public void onStart(NuProcess np)
        {
            this.np = np;
        }

        @Override
        public void onExit(int i)
        {
        }

        private String append(ByteBuffer bb, StringBuilder into)
        {
            byte[] bytes = new byte[bb.remaining()];
            bb.get(bytes);
            String s = new String(bytes, UTF_8);
            into.append(s);
            return s;
        }

        @Override
        public void onStdout(ByteBuffer bb, boolean bln)
        {
            String s = append(bb, stdout);
        }

        @Override
        public void onStderr(ByteBuffer bb, boolean bln)
        {
            String s = append(bb, stderr);
        }

        @Override
        public boolean onStdinReady(ByteBuffer bb)
        {
            return false;
        }
    }

    @BeforeAll
    public static void writeScript() throws IOException
    {
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir"));
        String rnd = Integer
                .toString(abs(ThreadLocalRandom.current().nextInt()), 36)
                + "-" + Long.toString(System.currentTimeMillis(), 36);
        String script = "#!/bin/sh\n\necho hello $1\nsleep 1\necho goodbye $1\n"
                + "echo really goodbye $1 1>&2\n";
        file = tmp.resolve(rnd + ".sh");
        Files.write(file, script.getBytes(UTF_8), CREATE, WRITE,
                TRUNCATE_EXISTING);
        Set<PosixFilePermission> perms = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
        );
        Files.setPosixFilePermissions(file, perms);

        String inputScript = "#!/bin/sh\n"
                + "\n"
                + "echo Give me some input!\n"
                + "read INPUT\n"
                + "\n"
                + "echo\n"
                + "echo Your input was\n"
                + "echo $INPUT";
        rnd = Integer
                .toString(abs(ThreadLocalRandom.current().nextInt()), 36)
                + "-" + Long.toString(System.currentTimeMillis(), 36);
        inputFile = tmp.resolve(rnd + ".sh");
        Files.write(inputFile, inputScript.getBytes(UTF_8), CREATE, WRITE,
                TRUNCATE_EXISTING);
        Files.setPosixFilePermissions(inputFile, perms);
    }

    @AfterAll
    public static void deleteScript() throws IOException
    {
        if (file != null && Files.exists(file))
        {
            Files.delete(file);
        }
    }

}
