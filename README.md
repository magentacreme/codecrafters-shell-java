# codecrafters-shell-java

A Java implementation of the CodeCrafters “Build Your Own Shell” challenge.

## What this shell supports

- Builtins: `echo`, `exit`, `pwd`, `cd`, `type`, `complete`, `jobs`, `history`, `declare`
- External command execution via `PATH`
- Pipelines (`|`)
- Output/error redirection (`>`, `>>`, `2>`, `2>>`)
- Background jobs (`&`) and job status reporting
- Basic variable expansion (`$VAR`, `${VAR}`)
- Interactive completion using JLine, including custom completion hooks
- History tracking with `HISTFILE` support

## Project structure

- Main entrypoint: `/src/main/java/Main.java`
- Builtin commands: `/src/main/java/command`
- Parsing utilities: `/src/main/java/parser`
- Job management: `/src/main/java/process`
- Completion system: `/src/main/java/autocomplete`
- Shell environment handling: `/src/main/java/env`

## Requirements

- Java 26 (preview features enabled)
- Maven

## Run locally

```sh
./your_program.sh
```

This script builds the project and starts the shell.

## Build manually

```sh
mvn -q -B package -Ddir=/tmp/codecrafters-build-shell-java
```

## Run tests/checks

```sh
mvn -q -B test
```

## Submit to CodeCrafters

```sh
codecrafters submit
```
