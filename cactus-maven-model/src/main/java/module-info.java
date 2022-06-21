open module cactus.maven.model
{
    requires jdk.xml.dom;
    requires java.logging;
    requires com.mastfrog.function;
    requires com.mastfrog.preconditions;

    exports com.telenav.cactus.maven.model;
    exports com.telenav.cactus.maven.model.dependencies;
    exports com.telenav.cactus.maven.model.resolver;
}
