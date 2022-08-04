open module cactus.source.analysis {

    requires cactus.maven.model;
    requires com.mastfrog.function;
    requires com.mastfrog.concurrent;
    requires cactus.maven.log;
    exports com.telenav.cactus.analysis;
    exports com.telenav.cactus.analysis.codeflowers;
}
