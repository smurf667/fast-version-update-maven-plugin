# "Fast" version updates in Maven

A wrapper around the [Versions Maven Plugin](https://www.mojohaus.org/versions-maven-plugin/).
Why? Because in my observation for complex, real-life builds, the original plugin checks for updates
for tons of transitive dependencies which were never declared, leading to a long run time, if your
(company) repository is slow (looking at you there, Nexus).

The wrapper tries to figure out declared and transitive dependencies to populate the
"includes" and "excludes" lists accordingly, which can speed up the process significantly
if there is a large number of transitive dependencies, e.g., when using Spring Boot as
the parent.

Build the plugin with

	mvn install

It can then be applied to a build, for example change to `examples/spring-boot-multi-module` and run

	mvn de.engehausen:fast-version-update-maven-plugin:update -DgenerateBackupPoms=false

This will touch the parent, properties and dependencies and update to latest versions.

Doing the same with the original plugin, for example

	mvn org.codehaus.mojo:versions-maven-plugin:use-latest-versions org.codehaus.mojo:versions-maven-plugin:update-properties org.codehaus.mojo:versions-maven-plugin:update-parent -DgenerateBackupPoms=false

might be significantly slower.

The plugin can also be used to just dump the computed "includes" and "excludes" lists:

	mvn de.engehausen:fast-version-update-maven-plugin:update -Dgoals=dump [-DdumpFile=data.properties]

⚠️ The plugin will **not** handle dependencies directly declared in submodules. It is best practice
to declare all dependencies of a multi-module project in the dependency management section of
the root `pom.xml`.

## Parameters of `fast-version-update-maven-plugin`

Parameters of the Mojo:

- `goals` - the goals to execute on the Versions plugin; `-Dgoals=use-latest-versions,update-properties,update-parent` by default
- `versionsVersion` - the version of the Versions plugin; `-DversionsVersion=2.8.1` by default
- `dumpFile` - for use with `-Dgoals=dump` to specify the file to write to, instead of just outputting to the console

Parameters of the Versions plugin can be passed as well, e.g. `-DgenerateBackupPoms=false` or `-DrulesUri=...`

These parameters are computed and cannot be used:

- `excludes`
- `excludesList`
- `includes`
- `includesList`
