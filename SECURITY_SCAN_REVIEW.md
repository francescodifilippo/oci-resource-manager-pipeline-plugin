# Jenkins Security Scan triage

Reviewed: 2026-10-08. Scanner: Jenkins-specific CodeQL pack used by Jenkins Security Scan.

The initial scan reported one potential `jenkins/plaintext-storage` finding:

| Source | Field | Maintainer assessment |
| --- | --- | --- |
| `OciRmProvisionStep` | `planOperationKey` | Non-secret, build-local correlation key identifying a previous PLAN operation. It is intentionally persisted as a Pipeline step argument; it is not a private key, passphrase, or OCI authentication token. |

This is a maintainer review, not blanket suppression or security certification.

OCI API private keys and optional passphrases must remain in Jenkins Credentials and must not enter `RemoteOperation.metadata`, Pipeline source, or logs.

## Temporary workflow arrangement

While `iac-pipeline-api:0.1.0-SNAPSHOT` is not available from Jenkins Maven repositories, the provider uses the Jenkins-specific CodeQL query pack in a workflow that first builds and installs its unpublished core dependency. After publishing `iac-pipeline-api`, switch back to the standard Jenkins reusable security scan workflow and recheck results.
