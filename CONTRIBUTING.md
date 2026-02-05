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
   
3. Build the project:
   ```bash
    mvn clean install
    ```
This will generate the package JAR in your local `.m2` repository, making it available for other local Maven projects.
    
4. (Optional) Run deploy to a local directory for verification:
   ```bash
    mvn deploy -DaltDeploymentRepository=local::default::file://$(pwd)/target/staging-deploy
    ```

## Release Process

This repository uses [Release Please](https://github.com/googleapis/release-please) to automate the release process.

1.  **Pull Requests**: When code is merged into the `main` branch, Release Please analyzes the commit messages (which must follow [Conventional Commits](https://www.conventionalcommits.org/)).
2.  **Release PR**: Release Please creates or updates a special "Release PR" that updates the `CHANGELOG.md` and bumps the version in `pom.xml`.
3.  **Release Creation**: When a maintainer approves and merges the Release PR, Release Please tags the commit, creates a GitHub Release, and triggers the publishing workflow (to Maven Central).

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
Agreement](https://cla.developers.google.com/about) (CLA). You (or your employer) retain the copyright to your
contribution; this simply gives us permission to use and redistribute your
contributions as part of the project. Head over to
<https://cla.developers.google.com/> to see your current agreements on file or
to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

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

2. Use CompletableFuture for all network operations to maintain the async-first architecture.

3. Keep dependencies minimal. Do not add new libraries unless absolutely necessary.

4. Include Javadoc for public interfaces.

## Community Guidelines

This project follows
[Google's Open Source Community Guidelines](https://opensource.google/conduct/).
