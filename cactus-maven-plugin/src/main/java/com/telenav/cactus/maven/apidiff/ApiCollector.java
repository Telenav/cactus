package com.telenav.cactus.maven.apidiff;

import com.sun.tools.javac.api.JavacTaskImpl;
import com.telenav.cactus.maven.log.BuildLog;
import com.telenav.cactus.util.PathUtils;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singleton;
import static javax.tools.StandardLocation.*;

/**
 *
 * @author timb
 */
public class ApiCollector
{
    private final MavenProject prj;
    private final MavenSession sess;
    private final BuildLog log;

    public ApiCollector(MavenProject prj, MavenSession sess, BuildLog log)
    {
        this.prj = prj;
        this.sess = sess;
        this.log = log;
    }

    private Set<String> classpath() throws Exception
    {
        Set<String> all = new LinkedHashSet<>(prj.getCompileClasspathElements());
        all.addAll(prj.getRuntimeClasspathElements());
        return all;
    }

    private static Set<File> toFiles(Collection<String> elements) throws MalformedURLException, URISyntaxException
    {
        Set<File> all = new LinkedHashSet<>(elements.size());
        for (String s : elements)
        {
            URL url = new URL(s);
            all.add(new File(url.toURI()));
        }
        return all;
    }

    private boolean isModular() throws MalformedURLException, URISyntaxException
    {
        Set<File> files = toFiles(prj.getCompileSourceRoots());
        for (File f : files)
        {
            if (new File(f, "module-info.java").exists())
            {
                return true;
            }
        }
        return false;
    }

    private File sourceOutput() throws IOException
    {
        Path dir = PathUtils.temp().resolve("ApiCollector-Compile-" + Long
                .toString(System
                        .currentTimeMillis(), 36)
                + "-" + Integer.toString(ThreadLocalRandom.current().nextInt(),
                        36));
        Files.createDirectories(dir);
        return dir.toFile();
    }

    public void run() throws Exception
    {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DL dl = new DL();
        StandardJavaFileManager mgr = compiler.getStandardFileManager(dl,
                Locale.US,
                UTF_8);
        Set<File> classpath = toFiles(classpath());
        File sourceOut = sourceOutput();
        try
        {
            Set<File> sourceRoots = toFiles(prj.getCompileSourceRoots());
            boolean modular = isModular();
            if (isModular())
            {
                mgr.setLocation(MODULE_PATH, classpath);
                mgr.setLocation(MODULE_SOURCE_PATH, sourceRoots);
            }
            else
            {
                mgr.setLocation(CLASS_PATH, classpath);
                mgr.setLocation(SOURCE_PATH, sourceRoots);
            }
            mgr.setLocation(SOURCE_OUTPUT, singleton(sourceOut));
            mgr.setLocation(CLASS_OUTPUT, singleton(sourceOut));

            JavacTaskImpl task = (JavacTaskImpl) compiler.getTask(dl, mgr, dl,
                    options(), Collections.emptyList(),
                    javaSources(mgr, sourceRoots));
            
            Boolean result = task.call();
        }
        finally
        {
            PathUtils.deleteFolderTree(sourceOut.toPath());
        }
    }

    private Set<JavaFileObject> javaSources(StandardJavaFileManager mgr,
            Set<File> sourceRoots) throws IOException
    {
        Set<JavaFileObject> result = new LinkedHashSet<>();
        for (File file : sourceRoots)
        {
            Path root = file.toPath();
            try ( Stream<Path> s = Files.walk(root, 1000).filter(p -> p
                    .getFileName().toString().endsWith(".java") && !Files
                    .isDirectory(p)))
            {
                Arrays.asList(mgr.getJavaFileObjects(s.toArray(Path[]::new)))
                        .forEach(result::add);
            }
        }
        return result;
    }

    private List<String> options()
    {
        List<String> result = new ArrayList<>();
        return result;
    }

    class DL extends Writer implements DiagnosticListener
    {
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException
        {
            log.info(new String(cbuf, off, len));
        }

        @Override
        public void flush() throws IOException
        {
            // do nothing
        }

        @Override
        public void close() throws IOException
        {
            log.info("Compile completed");
            // do nothing
        }

        @Override
        public void report(Diagnostic diagnostic)
        {
            log.info(diagnostic.toString());
        }

    }
}
