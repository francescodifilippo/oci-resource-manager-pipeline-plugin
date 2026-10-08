# Dependency license review (pre-release)

Project license: **MIT**, copyright (c) 2026 Francesco Di Filippo.

This is a preliminary review of direct dependencies declared in the source POM. It is not a substitute for reviewing the resolved production/runtime dependency tree and the generated HPI before distribution.

| Direct dependency | Use | Preliminary license status |
| --- | --- | --- |
| `iac-pipeline-api` | shared Jenkins lifecycle | MIT (same project family) |
| Jenkins Pipeline / Credentials plugin dependencies | Jenkins integration | review the exact BOM-resolved versions before release |
| `oci-java-sdk-resourcemanager` | OCI Resource Manager API | Oracle OCI Java SDK is dual-licensed UPL-1.0 or Apache-2.0 |
| `oci-java-sdk-common-httpclient-jersey` | OCI SDK HTTP transport | same OCI Java SDK license family; inspect bundled notices |
| Jackson BOM / Jackson libraries | SDK dependency alignment | Apache-2.0; verify exact bundled artifacts |
| `junit-jupiter` | tests only | EPL-2.0 |
| `jenkins-test-harness` | tests only | Jenkins test infrastructure; not intended as HPI runtime content |

## Mandatory pre-release checks

1. Run `mvn -B -ntp dependency:tree` and capture exact production/runtime versions.
2. Inspect the generated HPI for bundled libraries and required NOTICE/license files.
3. Confirm OCI SDK third-party notices and transitive licenses for the exact SDK version.
4. Resolve any license convergence or notice issue before public distribution.

Jenkins preparation guidance: https://www.jenkins.io/doc/developer/publishing/preparation/
