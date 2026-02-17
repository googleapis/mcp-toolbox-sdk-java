# Development

Below are the details to set up a development environment and run tests for the `mcp-toolbox-sdk-java` SDK.

## Versioning

This library adheres to [Semantic Versioning](http://semver.org/). 

## Processes

### Conventional Commit Messages

This repository utilizes [Conventional
Commits](https://www.conventionalcommits.org/) for structuring commit messages.

## Install and Run Locally
1. Clone the repository:
    ```bash
    git clone https://github.com/googleapis/mcp-toolbox-sdk-java
    ```

2. Navigate to the project directory:
   ```bash
    cd mcp-toolbox-sdk-java
    ```

3. Maven clean install:
   ```bash
    mvn clean install
    ```
This would directly generate the package JAR in the local (Developement env's) .m2 directory.

But if you want to generate it in a specific directory:

1. Create a directory to store the JAR:
   ```bash
    mkdir -p gcs-repo
    ```

2. Run deploy using $(pwd) to get the full path:
   ```bash
    mvn deploy -DaltDeploymentRepository=gcs-repo::default::file://$(pwd)/gcs-repo
    ```
This tells Maven to deploy to a local folder named `gcs-repo` in the correct directory structure.

Use this package in your test application.

## Testing

### Local Tests

To run tests locally, ensure you have the necessary dependencies and a running
MCP Toolbox service.

1. Create a new Java project (differnet from the SDK project).
2. Copy the source code from [`example`](./example/) folder of this repo ([ExampleUsage.java](./example/ExampleUsage.java)) to the `src`.
3. Copy the [`pom.xml`](./example/pom.xml) content from [`example`](./example/) to the new project.
4. Make the necesary changes for your `YOUR_TOOLBOX_SERVICE_ENDPOINT` and the `YOUR_CREDENTIALS_JSON_FILE_PATH` placeholders.
5. Run the following command to test:
   ```bash
   mvn clean compile exec:java -Dexec.mainClass="cloudcode.helloworld.ExampleUsage"
   ```

## Linting and Formatting

This project uses `com.spotify.fmt:fmt-maven-plugin` for formatting.

Format your code using
```bash
mvn com.spotify.fmt:fmt-maven-plugin:format
```

# Contributing to the SDK

We love your input! We want to make contributing to this project as easy and transparent as possible, whether it's:

1. Reporting a bug

2. Discussing the current state of the code

3. Submitting a fix

4. Proposing new features

5. Becoming a maintainer

### We Develop with Github

We use github to host code, to track issues and feature requests, as well as accept pull requests.

## Contribution Process

We welcome contributions to this project! Please review the following guidelines
before submitting.

### Contributor License Agreement

Contributions to this project must be accompanied by a [Contributor License
Agreement](https://cla.developers.google.com/about) (CLA). Read more [here](./CONTRIBUTING.md)

### Code Reviews

All submissions, including those by project members, require review. We use
[GitHub pull requests](https://help.github.com/articles/about-pull-requests/)
for this purpose.

* Ensure your pull request clearly describes the changes you are making.
* Ideally, your pull request should include code, tests, and updated
  documentation (if applicable) in a single submission.
* A reviewer from our team will typically review your
  PR within 2-5 days and may request changes or approve it.

### Report bugs using Github's issue tracker

We use GitHub issues to track public bugs. Report a bug by opening a new issue, it's that easy!

Great Bug Reports tend to have:

1. A quick summary and/or background

2. Steps to reproduce

3. Be specific!

4. Give sample code if you can.

5. What you expected would happen

6. What actually happened

7. Notes (possibly including why you think this might be happening, or stuff you tried that didn't work)

### Coding Style & Standards

1. We use standard Java naming conventions.

2. Use `CompletableFuture` for all network operations to maintain the async-first architecture.

3. Keep dependencies minimal. Do not add new libraries unless absolutely necessary.

4. Include Javadoc for public interfaces.

5. For more info, see [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)

