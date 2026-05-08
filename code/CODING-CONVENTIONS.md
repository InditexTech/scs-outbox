# Coding Conventions for Java projects

Here's a list of useful resources that everyone should check before contributing to this project.

## Table of Contents

1. [Java Styleguide](#java-styleguide)
   * [Important Changes from Google Java Style](#important-changes-from-google-java-style)
2. [Maven Conventions](#maven-conventions)
   * [POM Reference Structure](#pom-reference-structure)
   * [Adding new dependencies or plugins](#adding-new-dependencies-or-plugins)
3. [Documentation Styleguide](#documentation-styleguide)


## Java Styleguide

To maintain consistency in the format of our codebase, we use three open-source tools (see [ADR-0002](../docs/adr/0002-code-formatting-toolchain.md) for the full rationale and responsibility matrix):

- **[Spotless](https://github.com/diffplug/spotless/tree/main/plugin-maven)** (`com.diffplug.spotless:spotless-maven-plugin`) — enforces code formatting using the Eclipse formatter config at `src/main/config/eclipse-java-google-style.xml` and the import order at `src/main/config/eclipse-java-google-style.importorder`.
- **[maven-checkstyle-plugin](https://maven.apache.org/plugins/maven-checkstyle-plugin/)** — enforces structural and naming style rules using the Checkstyle ruleset at `src/main/config/checkstyle-java-google-style-17.xml`. Does **not** duplicate rules already covered by Spotless (import ordering, Java line length).
- **[sortpom-maven-plugin](https://github.com/Ekryd/sortpom)** (`com.github.ekryd.sortpom:sortpom-maven-plugin`) — enforces POM element ordering using the convention defined in `src/main/config/pom-code-convention.xml`.

All three run automatically during the `validate` Maven phase and are enforced in CI.

### Developer Commands

**Auto-format Java code before committing:**
```bash
mvn -f code/pom.xml spotless:apply
```

**Sort POM files before committing:**
```bash
mvn -f code/pom.xml com.github.ekryd.sortpom:sortpom-maven-plugin:sort
```

**Check formatting, style and POM order (what CI does):**
```bash
mvn -f code/pom.xml validate
```

### IDE Setup

There are formatter configuration files for the main IDEs used:
  -  Eclipse Java Formatter configuration file located at `src/main/config/eclipse-java-google-style.xml` & Eclipse Organize Imports configuration located at `src/main/config/eclipse-java-google-style.importorder`. See how to configure them in the [Coding Formatting in Eclipse guide](https://help.eclipse.org/2020-06/index.jsp?topic=%2Forg.eclipse.jdt.doc.user%2Freference%2Fpreferences%2Fjava%2Fcodestyle%2Fref-preferences-formatter.htm).
  -  IntelliJ Code Formatting configuration file located at `src/main/config/intellij-java-google-style.xml`.  See how to [configure Code Formatting in IntelliJ](https://www.jetbrains.com/help/rider/Enforcing_Code_Formatting_Rules.html#using-comments-to-configure-formatter).

However, keep in mind that **the formatters are just an aid when writing code**, and can introduce errors. _The developer is ultimately responsible for the code he/she publishes_.

### Important Changes from Google Java Style

Checkstyle ruleset is based on the [Google Java Style](https://google.github.io/styleguide/javaguide.html), with the following modifications adapted to the current conventions used in the company:

#### [Source file structure](https://google.github.io/styleguide/javaguide.html#s3-source-file-structure)

**The structure** to be followed in Java files would be the following (unlike Google Style):

1. **Package statement**
2. **Import statements**
3. **Exactly one top-level class**

_Exactly **one blank line** separates each section that is present._

*It is NOT ALLOWED to use license or copyright headers in code files. If you need to add the license / copyright, it is recommended to add them as a separate file for the entire project, not class by class. You can take a look to [the GitHub documentantion on licensing a repository](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)*.

#### [Imports ordering](https://google.github.io/styleguide/javaguide.html#s3.3.3-import-ordering-and-spacing)

Imports should be ordered as follows:
  1. All static imports divided in 3 blocks: *java.\*, dev.inditex.\* and any other import*, **sorted alphabetically** .
  2. All non-static imports divided in 3 blocks: *java.\*, dev.inditex.\* and any other import*, **sorted alphabetically**.

_Exactly **one blank line** separates each block that is present._

#### [Column limit](https://google.github.io/styleguide/javaguide.html#s4.4-column-limit)

The column limit is increased to **140** characters, in the Inditex Checkstyle ruleset.

## Maven Conventions

The tool to build, package, test and verify the project is [Apache Maven](https://maven.apache.org/index.html). A Maven Project is defined by one or more [POM xml files](https://maven.apache.org/pom.html#What_is_the_POM).

### POM Reference Structure

-  POM files must be ordered according to the criteria defined in the [Sortpom Maven Plugin](https://github.com/Ekryd/sortpom) configuration, which is located at `src/main/config/pom-code-convention.xml`.
   -  This convention is based on the [POM Code Convention](https://maven.apache.org/developers/conventions/code.html#pom-code-convention), the standard convention adopted by the Maven community. Take a look at [the following Answerhub article on using Sortpom](https://inditex.cloud.answerhub.com/articles/1525/sortpom-maven-plugin-guide.html).

- The project code layout follows the [Maven Standard Directory Layout](https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html)

- The dependencies version (including local modules' version) should be placed in the `<dependencyManagement>` parent POM section. Take a look to this guide about [Consolidating dependencies on Dependency Management](https://maven.apache.org/pom.html#Dependency_Management).
  - *This criterion also applies to `plugins` and `pluginManagement` sections*
- [Properties](https://maven.apache.org/pom.html#properties) should be defined in the parent POM, whenever possible, and default properties should be separated from project specific properties by a **blank line**.

### Adding new dependencies or plugins

Adding new features or plugins to our codebase sometimes imply adding also new dependencies in the Maven configuration. When this is the case always remember that within `<dependency>` blocks we group them as follows:
1. Local project modules
2. Framework, internal Inditex dependencies
3. 3rd party, external dependencies
4. All test scope dependencies

Each group **separated by one blank line**.

Apply the same caution when a new plugin is needed for the Maven build execution. Include them always following alphabetical order by groupId and artifactId.

##  Documentation Styleguide

Check out Github's [Mastering Markdown](https://guides.github.com/features/mastering-markdown/) to learn about the basic syntax and _GitHub Flavored Markdown_.
