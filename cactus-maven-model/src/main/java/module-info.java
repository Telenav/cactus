open module cactus.maven.model
{
    requires jdk.xml.dom;
    requires java.logging;
    requires java.net.http;
    requires com.mastfrog.function;
    requires com.mastfrog.preconditions;
    requires cactus.maven.xml;

    exports com.telenav.cactus.maven.model;
    exports com.telenav.cactus.maven.model.internal;
    exports com.telenav.cactus.maven.model.dependencies;
    exports com.telenav.cactus.maven.model.resolver;
    exports com.telenav.cactus.maven.model.published;
}
