# BOM Decomposer

The project includes various utilities that help analyze managed dependencies of a project
and suggest version changes to avoid potential conflicts among the dependencies.

## Multi module release detection

One of the utilities included is a multi module release detector. It is a best-effort
(not 100% accurate) utility that is trying to identify the origin (e.g. a git repo or
another ID) of the artifacts from the effective set of the project's managed dependencies and
in case multiple releases from the same origin are detected it reports them as conflicts.

NOTE: the utility will be resolving every managed dependency as part of the analyses!

The utility can invoked using a public API or a Maven plugin with a minimal configuration below
```
    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>report-release-versions</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

which should be added to the `pom.xml`. With this minimal configuration the conflicts will be
logged as a `WARNING`.

Here is a complete set of supported options
```
    <build>
        <plugins>
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <configuration>
                    <!-- Whether to skip this goal during the build -->
                    <skip>${skipBomReport}</skip>

                    <!-- wWether to generate the HTML report, the default is true -->
                    <htmlReport>${bomHtmlReport}</htmlReport>

                    <!--
                       Whether to report all the detected release origins
                       or only those with the conflicts, the default is false (only the conflicts)
                    -->
                    <reportAll>${bomReportAll}</reportAll>

                    <!-- The default level to use for report logging, the default is DEBUG -->
                    <reportLogging>${bomReportLogging}</reportLogging>

                    <!--
                      How to handle a detected conflict. The allowed values are:
                      * WARN - log a warning (the default)
                      * ERROR - log an error and fail the build
                      * INFO - log an info message
                      * DEBUG - log a debug message
                    -->
                    <bomConflict>${bomConflict}</bomConflict>

                    <!--
                      How to handle a detected resolvable version conflict. I.e. in case
                      the preferred version of the artifact was found to be available in the Maven
                      repository. Allowed values are:
                      * WARN - log a warning
                      * ERROR - log an error and fail the build (the default)
                      * INFO - log and info message
                      * DEBUG - log a debug message
                    -->
                    <bomResolvableConflict>${bomResolvableConflict}</bomResolvableConflict>

                    <!--
                      Whether to skip checking the conflicting dependencies for available versions updates
                      picking the latest version found in the BOM as the preferred one to align with.
                      The default is false.
                    -->
                    <skipUpdates>${bomSkipUpdates}</skipUpdates>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>report-release-versions</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
```

## Quarkus Platform BOM Generator

Another tools is a Quarkus platform BOM generator. Currently, it takes a `pom.xml` file as an input. This `pom.xml` is supposed to be
importing `io.quarkus:quarkus-bom` and other extension BOMs (extension and other dependencies added directly to its `dependencyManagement` is also supported).
The direct managed dependencies found in the original platform BOM are analyzed and categoried:
1) import of `io.quarkus:quarkus-bom` BOM;
2) imports of other extension BOMs;
3) direct dependencies on extension artifacts.

All the dependencies coming from the `io.quarkus:quarkus-bom` will be copied unmodified to the generated platform BOM.

At the same time `io.quarkus:quarkus-bom` will be decomposed into project releases using the principle described above.

The dependencies imported from other BOMs will be handled in the following way:
1) if an imported extension BOM was found to be importing `io.quarkus:quarkus-bom` then the content of the imported `io.quarkus:quarkus-bom` will be
subtracted from the extension BOM;
2) each extension BOM will be decomposed into project releases using the principle described above;
3) if a dependency from an extension BOM does not appear to belong to any of the project releases detected in any other BOM
(`io.quarkus:quarkus-bom` and any other extension BOM) then it is copied to the generated platform BOM;
4) if a dependency from an extension BOM appears to belong to a project whose release is found in `io.quarkus:quarkus-bom` BOM
then the version of that project release will be the preferred version for that dependency (if the dependency with this version actually exists,
otherwise the original version of the dependency will be added to the generated platform);
5) if a dependency from an extension BOM appears to belong to a project whose release is found in another extension BOM (but not in `io.quarkus:quarkus-bom`)
then whatever version appears to be newer than the other one will be used as the preferred version for that dependency and all the other artifacts that belong to the same project release.

### Configuration

The `pom.xml` that defines the BOM can simply add the following plugin
```
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <executions>
                    <execution>
                        <phase>process-resources</phase>
                        <goals>
                            <goal>generate-platform-bom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
```

The plugin will generate the BOM which will replace the current `pom.xml` and will be installed in the repository in place of the original `pom.xml`.

It is possible to enforce versions on certain dependencies or exclude them altogether from the generated BOM. E.g.

```
            <plugin>
                <groupId>io.quarkus</groupId>
                <artifactId>quarkus-platform-bom-maven-plugin</artifactId>
                <version>999-SNAPSHOT</version>
                <executions>
                    <execution>
                        <phase>process-resources</phase>
                        <goals>
                            <goal>generate-platform-bom</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <enforcedDependencies>
                        <dependency>org.javassist:javassist:30</dependency>
                    </enforcedDependencies>
                    <excludedDependencies>
                        <dependency>com.amazon.alexa:ask-sdk-apache-client</dependency>
                    </excludedDependencies>
                    <excludedGroups>
                        <excludedGroup>ch.qos.logback</excludedGroup>
                    </excludedGroups>
                </configuration>
            </plugin>
```

The generated BOM will enforce version `30` on artifact `org.javassist:javassist`, will not include `com.amazon.alexa:ask-sdk-apache-client` and will not include artifacts with groupId `ch.qos.logback`.

### Output

The plugin will create the `target/bom` directory which will contain the generated BOMs and various reports in HTML format.
Right under `target/boms` there will be the main `index.html` file and directories named after BOM artifact coordinates using the following format `<groupId>.<artifactId>-<version>`. E.g.

````
com.datastax.oss.quarkus.cassandra-quarkus-bom-1.0.0-alpha3
com.datastax.oss.quarkus.cassandra-quarkus-bom-deployment-1.0.0-alpha3
com.hazelcast.quarkus-hazelcast-client-bom-1.0.0
index.html
io.quarkus.quarkus-universe-bom-999-SNAPSHOT
org.amqphub.quarkus.quarkus-qpid-jms-bom-0.18.0
org.apache.camel.quarkus.camel-quarkus-bom-1.0.0
org.kie.kogito.kogito-bom-0.13.1
org.optaplanner.optaplanner-bom-7.41.0.Final
````

There will be a directory for each BOM (i.e. the platform BOM and every imported extension BOM). In the example above, the directory containing the platform BOM is `io.quarkus.quarkus-universe-bom-999-SNAPSHOT`.

Each directory currently contains:
* pom.xml - the generated `BOM`. If it's in the directory of the Quarkus platform BOM then it's the complete generated platform BOM. If it's in the directory of an extenion BOM, it's the extension BOM adapted to the platform. I.e. it contains only the dependencies relevant to the extension but all the versions it contains are aligned with those in the generated platform BOM.
* diff.html - an HTML report highliting the differences between the original version of the BOM and the generated one (dependencies added, missing, downgraded, upgraded, matching).
* original-releases.html - multi-module releases detected in the original version of the BOM.
* generated-releases.html - multi-module releases detected in the generated version of the BOM.
