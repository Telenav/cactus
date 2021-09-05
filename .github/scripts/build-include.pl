#!/usr/bin/perl

#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
#
#  © 2011-2021 Telenav, Inc.
#  Licensed under Apache License, Version 2.0
#
#///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

use strict;
use warnings;

my $WORKSPACE = `pwd`;
$WORKSPACE =~ s/\n+//g;

my $KIVAKIT_HOME = "$WORKSPACE/kivakit";
my $MESAKIT_HOME = "$WORKSPACE/mesakit";

$ENV{$KIVAKIT_HOME} = $KIVAKIT_HOME;
$ENV{$MESAKIT_HOME} = $MESAKIT_HOME;

#
# Print the given string
#

sub say
{
    my ($text) = @_;

    print "===> $text\n"
}

#
# Run the given command in bash
#

sub run
{
    my ($command) = @_;

    say("Executing: $command");
    my $exit_code = system($command);

    if ($exit_code != 0)
    {
        die "Command failed: $command";
    }
}

#
# Checks the given branch name
#

sub check_branch
{
    my ($branch) = @_;

    die "Must supply a branch" if (!defined $branch || $branch eq "");
}

#
# Checks the pull request identifier
#

sub check_pull_request_identifier
{
    my ($identifier) = @_;

    die "Must supply a pull request identifier" if ($identifier eq "");
    die "Pull request identifier must be a number" if (!($identifier =~ /d+/));
}

#
# Checks the build type
#

sub check_build_type
{
    my ($build_type) = @_;

    die "Must supply build type" if (!defined $build_type || $build_type eq "");
    die "Unrecognized build type: $build_type" if (!($build_type eq "package" || $build_type eq "publish"));
}

#
# Check the given repository name, returning the full GitHub URL if it is valid
#

sub check_repository
{
    my ($repository) = @_;

    die "Must supply a repository" if ($repository eq "");
    die "Must supply a github repository URL" if (index($repository, "https://github.com/Telenav/") != 0);
}

#
# Converts a GitHub ref like "refs/heads/feature/lasers" into a branch name like "feature/lasers"
#

sub reference_to_branch
{
    my ($reference) = @_;

    if ($reference =~ m!.*/(\d+)/.*!)
    {
        my $identifier = $1;
        check_pull_request_identifier($identifier);
        return "pull/$identifier"
    }

    $reference =~ s!refs/heads/!!;

    return $reference;
}

#
# Install the pom.xml file in the current folder
#

sub install_pom
{
    my ($folder) = @_;

    say("Installing super POM in $folder");
    run("cd $folder && mvn --batch-mode --no-transfer-progress clean install");
}

#
# Clones the given repository and branch
#

sub clone
{
    my ($repository, $from_branch) = @_;
    check_repository($repository);
    check_branch($from_branch);

    say("Cloning $repository ($from_branch)");

    if (index($from_branch, "pull/") == 0)
    {
        run("cd $WORKSPACE && clone $repository master && git fetch origin '$from_branch/head:pull-request' && git checkout pull-request");
    }
    else
    {
        run("cd $WORKSPACE && git clone --branch $from_branch --quiet $repository");
    }
}

#
# Build project
#

sub build
{
    my ($build_type, $folder) = @_;
    my $passphrase = $ENV{"secrets.OSSRH_GPG_SECRET_KEY_PASSWORD"};
    check_build_type($build_type);

    say("Building $folder ($build_type)");

    if ($build_type eq "package")
    {
        run("cd $folder && mvn -Dmaven.javadoc.skip=true -DKIVAKIT_DEBUG='!Debug' -P shade -P tools --no-transfer-progress --batch-mode clean install");
    }
    elsif ($build_type eq "publish")
    {
        run("cd $folder && mvn -P attach-jars -P sign-artifacts -P shade -P tools --no-transfer-progress --batch-mode -Dgpg.passphrase='$passphrase' clean deploy");
    }
    else
    {
        die "Unrecognized build type: $build_type"
    }
}

sub build_kivakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    install_pom("$KIVAKIT_HOME/superpom");
    build($build_type, "$KIVAKIT_HOME");
}

sub build_kivakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-extensions");
}

sub build_kivakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/kivakit-examples");
}

sub build_lexakai
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai");
}

sub build_lexakai_annotations
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/lexakai-annotations");
}

sub build_mesakit
{
    my ($build_type) = @_;
    check_build_type($build_type);

    say("Installing shape file reader");
    run("mvn install:install-file -Dfile='$WORKSPACE/mesakit/mesakit-map/geography/libraries/shapefilereader-1.0.jar' -DgroupId=org.nocrala -DartifactId=shapefilereader -Dversion=1.0 -Dpackaging=jar");

    install_pom("$MESAKIT_HOME/superpom");
    build($build_type, $MESAKIT_HOME);
}

sub build_mesakit_extensions
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-extensions");
}

sub build_mesakit_examples
{
    my ($build_type) = @_;
    check_build_type($build_type);

    build($build_type, "$WORKSPACE/mesakit-examples");
}

