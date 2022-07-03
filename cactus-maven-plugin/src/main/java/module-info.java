module cactus.maven.plugin {
    requires transitive com.telenav.cactus.git;
    requires transitive com.telenav.cactus.maven.log;
    requires transitive cactus.maven.model;
    requires transitive com.telenav.cactus.cli;
    requires transitive com.telenav.cactus.util;
    requires transitive cactus.maven.versioning;
    requires transitive cactus.maven.scope;
    requires transitive cactus.maven.xml;
    requires transitive cactus.metadata;
    requires transitive cactus.source.analysis;
    requires java.net.http;
    requires java.xml;
    requires maven.plugin.annotations;
    exports com.telenav.cactus.maven.commit;
    exports com.telenav.cactus.maven;
    exports com.telenav.cactus.maven.common;
    exports com.telenav.cactus.maven.mojobase;
    exports com.telenav.cactus.maven.shared;
    exports com.telenav.cactus.maven.tree;
    exports com.telenav.cactus.maven.trigger;
}
