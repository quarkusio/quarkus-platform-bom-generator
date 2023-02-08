# Playground for dependency analysis and processing

This project contains various utilities and APIs that allow generating various reports about dependencies of a given project
as well as processing dependency trees in parallel from the bottom to the top.

## Project dependency reports organized by repository URLs and tags

This kind of report could be generated either using a CLI tool or a Maven plugin.

### CLI

The latest release of the CLI tool, currently named domino.jar, can be downloaded from https://github.com/quarkusio/quarkus-platform-bom-generator/releases

#### Generating dependency analysis reports for a local project

```
java -jar domino.jar report --project-dir=<project-dir> --log-modules-to-build
```

This command will identify modules that are configured to be published (installed/deployed) to a Maven repository, analyze their dependencies and generate
the report for the project grouping artifacts by their origins (source code location and tag) and sorting them according to their dependencies.
The first group of artifacts (representing a project release) in the report will not have dependencies on any other group of artifacts while the last group of artifacts
in the report will transitively depend on all of the artifact groups above it.

`--project-dir` could point to either Maven or Gradle project.

`--log-modules-to-build` will output artifacts coordinates as GAVs instead of GACTV format, which is what the build automation scripts expect.

#### Generating a report for a released project using its BOM

```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.7 --include-non-managed --log-modules-to-build
```

The command above will resolve the provided BOM artifact, collect dependencies of every constraint (managed dependency) present in the BOM and generate a complete
dependency report for the BOM.
The `--include-non-managed` argument will include dependencies of the BOM constraints that aren't managed in the BOM. If this argument is not provided
the report will contain only the constraints present in the BOM.

#### Generating a report for specific Maven artifacts

```
java -jar domino.jar report --root-artifacts=<g:a:v(,g:a:v)*> --log-modules-to-build
```

The command above will collect dependencies and generate a report for the provided artifacts.

#### Generating SBOMs

The only argument that needs to be added to generate an SBOM instead of a text file is `--manifest`.

For example, the following command will generate a text file containing Vert.X artifacts managed by the Vert.X BOM (the SCM info and summary could be disabled by adding `--log-code-repos=false --log-summary=false`):
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --output-file=report.txt
```

Adding the `--manifest` will turn the `report.txt` into an SBOM:
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --output-file=report.txt --manifest
```

The above report will include only the Vert.X artifacts. Adding `--include-non-managed` will include all the non-optional dependencies of Vert.X artifacts in the report:
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --include-non-managed --output-file=report.txt --manifest
```

#### Maven Artifact Resolver Settings

The dependency analyzer will initialize a Maven artifact resolver taking into account user's Maven `settings.xml` file. The `report` command though allows passing custom Maven settings and/or activate specific profiles from the command line using the following arguments:

* `-s` or `--maven-settings` can be used to provide a customer `settings.xml` file;
* `-P` or `--maven-profiles` can be used to activate specific Maven profiles.

#### SCM Location

Domino is relying on the SCM locator library developed as part of the [Red Hat AppStudio's JVM buid service](https://github.com/redhat-appstudio/jvm-build-service/tree/main/java-components/build-recipes-database). The SCM locator library provides an API that fetches SCM info from a [GitHub repository](https://github.com/redhat-appstudio/jvm-build-data/tree/main/scm-info). It also allows configuring a fallback SCM locator.
The Domino application comes pre-configured to use the SCM locator library and an SCM locator that fetchs SCM info from POM artifacts as a fallback.

##### Fixing SCM location failures

In case an SCM location was found to be missing for some artifacts, it would have to be looked up "manually" and added by openning a PR in the [SCM info repository](https://github.com/redhat-appstudio/jvm-build-data). A brief guide how to do that is available in the [Dealing With Missing Artifacts](https://github.com/apheleia-project/apheleia/blob/main/docs/index.adoc#dealing-with-missing-artifacts-artifactbuildmissing) chapter.

A local clone of the SCM info repository could be passed to the Domino application with `--recipes` argument, whose value could be either a URL or a local filesystem path to the repository.
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --output-file=report.txt --recipe-repos=/path/to/recipe/repo
```

##### Warn on missing SCM info

By default, in case an SCM info couldn't be determined, the process will fail with a corresponding error. It is possible though to turn those errors into warnings in the logs by adding `--warn-on-missing-scm` argument to generate the complete report.
The SCM information present in the resulting report will not be valid but it could be used to get an idea of it would look like if all the SCM info was available.
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --output-file=report.txt --warn-on-missing-scm
```

##### Legacy SCM Locator

Before intergating the SCM locator library from the AppStudio, Domino used its own SCM locator implementation. The plan is to remove it completely in the future, however until it's removed, it can be used instead of the SCM locator from AppStudio by adding `--legacy-scm-locator` argument to the command line:
```
java -jar domino.jar report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --output-file=report.txt --legacy-scm-locator
```

### Maven plugin

There is also a Maven plugin goal that can be used to generate a dependency report. For example, here is one for Vert.X:

```
<?xml version="1.0" encoding="UTF-8"?>
<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.playground</groupId>
  <version>1.0-SNAPSHOT</version>
  <artifactId>deps-to-build</artifactId>
  <packaging>pom</packaging>
  <name>Vert.X Dependencies to Build</name>

  <properties>
    <vertx.version>4.3.7</vertx.version>
  </properties>

  <build>
    <defaultGoal>quarkus-platform-bom:deps-to-rebuild</defaultGoal>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>io.quarkus</groupId>
          <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
          <version>0.0.78</version>
          <configuration>
            <!-- The main BOM to productize -->
            <bom>io.vertx:vertx-dependencies:${vertx.version}</bom>
            <!-- the report file -->
            <outputFile>deps-to-build.txt</outputFile>
            <!-- log code repo info -->
            <logCodeRepos>true</logCodeRepos>
            <!-- log artifact coordinates in GAV format instead of GACTV -->
            <logModulesToBuild>true</logModulesToBuild>
            <!-- warn if some constraints couldn't be resolved instead of failing -->
            <warnOnResolutionErrors>true</warnOnResolutionErrors>

            <!--
            Top level artifacts to build. If not configured, all the dependencies with groupId
            matching the BOM groupId will be selected as top level artifacts to be built from source.
            <topLevelArtifactsToBuild>
              <artifact>io.vertx:vertx-codegen:${vertx.version}</artifact>
            </topLevelArtifactsToBuild>
            -->

            <!-- Dependency levels to build. If not configured only the managed dependencies will be selected on all levels
            <level>0</level>
            -->

            <!-- To include dependency trees in the report
            <logTrees>true</logTrees>
            -->

            <includeNonManaged>true</includeNonManaged>

            <!-- Dependency keys to exclude
            <excludeKeys>
              <key>io.vertx:vertx-jgroups</key>
            </excludeKeys>
            -->

            <!-- Exclude dependencies with specific groupIds
            <excludeGroupIds>
            </excludeGroupIds>
            -->

            <!-- Dependency keys to include
            <includeGroupIds>
              <groupId>io.opentelemetry</groupId>
            </includeGroupIds>
            -->

            <!-- Include dependencies with the following groupIds
            <includeKeys>
              <key>xxx</key>
            </includeKeys>
            -->
            
            <!-- Custom SCM info repo
            <recipeRepos>
              <repo>path/to/scm-info/repo</repo>
            </recipeRepos>
            -->
            <!-- Warn on missing SCM info
            <warnOnMissingScm>true</warnOnMissingScm>
            -->
            <!-- To use the legacy SCM locator
            <legacyScmLocator>true</legacyScmLocator>
            -->
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>io.quarkus</groupId>
        <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

Simply save the above XML in a `pom.xml` file and run `mvn` in the directory the `pom.xml` was stored. The report will be saved in `deps-to-build.txt` file.
