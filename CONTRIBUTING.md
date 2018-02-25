# How to Contribute to K9

K9 uses [Gradle](https://gradle.org/) as its build tool, and as such, requires very little work to set up.

Simply clone the git repository, and run your IDE task of choice, i.e. `gradlew eclipse` or `gradlew idea`, or install a Gradle plugin for your IDE. Then import the project.

### HELP! My code is full of errors!

This project uses [Lombok](https://projectlombok.org/), and requires some special IDE setup. Their website has install guides for most common IDEs:

- [Eclipse](https://projectlombok.org/setup/eclipse)
- [IntelliJ IDEA](https://projectlombok.org/setup/intellij)
- [Netbeans](https://projectlombok.org/setup/netbeans)

## Style Guide

I'm not too picky over code style, but I would like PRs to be consistent with the current code base. The basic rules to follow are:

- SPACES for indents
- Same-line braces (egyptian style)

## Nullness Annotations

I try to keep this project free of most/all warnings, and this includes nullability warnings. Please enable these in your IDE of choice, and use `@Nonnull`/`@Nullable`/`@ParametersAreNonnullByDefault` liberally. Most of the existing code already has these annotations, and the warnings will prevent you breaking its contracts.