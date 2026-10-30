# Release checklist — OCI Resource Manager Pipeline

Status: **pre-release**.

## Repository readiness

- [x] MIT license declared in POM and repository
- [x] Jenkins-compatible artifact ID and `io.jenkins.plugins` group ID
- [x] project URL, SCM metadata, and GitHub issue tracker declared in POM
- [x] Java 21 GitHub Actions build
- [x] root `Jenkinsfile` for future `ci.jenkins.io`
- [x] Dependabot, CODEOWNERS, PR template, CONTRIBUTING and security guidance
- [x] documentation-as-code README and Pipeline examples
- [x] Jenkins test-harness plugin wiring coverage
- [x] Focused PLAN-only, PLAN→APPLY and asynchronous PLAN→AWAIT→APPLY examples
- [ ] Validate OCI IAM guidance and job lifecycle mapping against a real non-production stack
- [ ] Validate `ociRmStack` creation (Git and Object Storage sources), its restart behaviour, and the Object Storage access requirement against a real non-production tenancy
- [x] Confirm third-party notices for the bundled OCI Java SDK dependency set ([THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md))

## Before Jenkins hosting / first release

- [ ] Release `iac-pipeline-api` first and replace the provider SNAPSHOT dependency with the released version
- [x] Complete resolved dependency-tree and bundled-license audit: the HPI bundles only the OCI SDK, resilience4j and vavr; Jersey, Jackson and HttpClient come from API plugins
- [ ] Validate against a real non-production provider endpoint
- [x] Add deeper Pipeline restart/abort integration coverage (`OciRmStackRestartTest`; abort while polling is covered in the core)
- [ ] Run Jenkins Plugin Compatibility Tester / plugin verifier
- [x] Perform an internal HPI installation on the target Jenkins baseline (2.568.3 on 2026-10-30: plugins resolved by the BOM, `agent none` Declarative builds; `otfWorkspace` and `terrakubeWorkspace` against a local stub, `ociRmStack` up to the real OCI endpoint (401 with a dummy key))
- [ ] Open Jenkins plugin hosting request
- [ ] After Jenkins forks the repository, update project/SCM URLs to the canonical `jenkinsci` repository
- [ ] Obtain Jenkins repository release permissions
- [ ] Publish release notes

Official hosting guide: https://www.jenkins.io/doc/developer/publishing/requesting-hosting/
