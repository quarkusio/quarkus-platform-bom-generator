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
java -jar domino.jar from-maven report --bom=io.vertx:vertx-dependencies:4.3.7 --include-non-managed --log-modules-to-build
```

The command above will resolve the provided BOM artifact, collect dependencies of every constraint (managed dependency) present in the BOM and generate a complete
dependency report for the BOM.
The `--include-non-managed` argument will include dependencies of the BOM constraints that aren't managed in the BOM. If this argument is not provided
the report will contain only the constraints present in the BOM.

#### Generating a report for specific Maven artifacts

```
java -jar domino.jar from-maven report --root-artifacts=<g:a:v(,g:a:v)*> --log-modules-to-build
```

The command above will collect dependencies and generate a report for the provided artifacts.

#### Generating a report for a released Gradle project

Gradle projects currently require a slightly different approach. In perspective though, the same general set of commands should work for both Maven and Gradle projects.

The first step to analyze a Gradle project would be to initialize one in the Domino tool:

```
java -jar domino.jar project create --name=kafka --repo-url=https://github.com/apache/kafka
```

The command above will create the `~/.domino` directory and clone Kafka code repository in there. Once that's been done, a dependency report could be generated using the following command:

```
java -jar domino.jar from-gradle --project=kafka --tag=3.3.1 --log-modules-to-build
```
The command will checkout the `3.3.1` tag in the cloned repository and print the report for it.

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
java -jar domino.jar from-maven report --bom=io.vertx:vertx-dependencies:4.3.4.redhat-00007 --include-non-managed --output-file=report.txt --manifest
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
          <version>0.0.72</version>
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
