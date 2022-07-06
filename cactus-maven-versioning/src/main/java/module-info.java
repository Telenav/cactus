open module cactus.maven.versioning {

    requires com.mastfrog.function;
    requires com.mastfrog.preconditions;
    requires cactus.maven.model;
    requires cactus.maven.scope;
    requires cactus.util;
    requires cactus.maven.xml;
    requires java.xml;

    exports com.telenav.cactus.maven.refactoring;
}
