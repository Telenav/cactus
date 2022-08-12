package com.telenav.cactus.cli;

import com.zaxxer.nuprocess.NuProcess;
import com.zaxxer.nuprocess.NuProcessBuilder;
import com.zaxxer.nuprocess.NuProcessHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
        System.out.println("OUTPUT " + output);
        assertEquals("hello The Test\n"
                + "goodbye The Test\n", output);
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
            System.out.println("prestart");
        }

        @Override
        public void onStart(NuProcess np)
        {
            System.out.println("start");
            this.np = np;
        }

        @Override
        public void onExit(int i)
        {
            System.out.println("on exit " + i);
            System.out.println("output was:");
            System.out.println(stdout);
            System.out.println("\nstderr was:");
            System.out.println(stderr);
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
            System.out.println("OUT: '" + s + "' " + bln);
        }

        @Override
        public void onStderr(ByteBuffer bb, boolean bln)
        {
            String s = append(bb, stderr);
            System.out.println("stdErr '" + s + "'");
        }

        @Override
        public boolean onStdinReady(ByteBuffer bb)
        {
            System.out.println("onStdin");
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
        System.out.println("FILE IS " + file);
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
