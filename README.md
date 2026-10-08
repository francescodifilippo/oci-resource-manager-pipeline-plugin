# OCI Resource Manager Pipeline

**Plugin ID:** `oci-resource-manager-pipeline`  
**Status:** pre-release

Declarative Jenkins Pipeline integration for Oracle Cloud Infrastructure Resource Manager. It uses the OCI Java SDK and the shared [IaC Pipeline API](https://github.com/francescodifilippo/iac-pipeline-api-plugin) to run `PLAN`, `APPLY`, and `DESTROY` jobs with durable asynchronous execution.

## Requirements

- Jenkins 2.568.3 or newer
- Java 21
- `iac-pipeline-api` of the matching tested version
- OCI Resource Manager stack
- OCI API-key identity with the required IAM permissions

## Installation

For local pre-release testing:

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

The generated HPI is under `target/`.

## Configuration

In **Manage Jenkins → System → OCI Resource Manager connections**, configure:

- connection name;
- OCI region;
- tenancy OCID;
- user OCID;
- API key fingerprint;
- Jenkins Secret Text credential containing the PEM private key;
- optional Secret Text credential containing the private-key passphrase.

Pipeline code refers only to the connection name. Private keys and passphrases are never persisted in `RemoteOperation`.

## Pipeline syntax

### `ociRmProvision`

| Parameter | Purpose |
| --- | --- |
| `connection` | configured OCI connection |
| `stackId` | Resource Manager stack OCID |
| `mode` | `plan`, `apply`, or `destroy` |
| `displayName` | optional OCI job display name |
| `planOperationKey` | apply a successful plan from the same Jenkins build |
| `planJobId` | direct OCI plan job OCID alternative |
| `autoApprove` | explicitly permit apply without a prior plan |
| `operationKey` | build-local correlation key |
| `waitForCompletion` | wait now or continue after submission |
| `pollingSeconds` | polling interval |
| `timeoutMinutes` | maximum Jenkins-side wait |

### `ociRmAwait`

Waits for a previously submitted OCI operation using its `operationKey`.

## Plan → apply example

```groovy
stage('Plan') {
  options {
    ociRmProvision(
      connection: 'oci-prod',
      stackId: 'ocid1.ormstack.oc1.eu-frankfurt-1.example',
      mode: 'plan',
      operationKey: 'oci-plan',
      waitForCompletion: true
    )
  }
  steps { echo 'Plan completed' }
}

stage('Apply') {
  options {
    ociRmProvision(
      connection: 'oci-prod',
      stackId: 'ocid1.ormstack.oc1.eu-frankfurt-1.example',
      mode: 'apply',
      planOperationKey: 'oci-plan',
      operationKey: 'oci-apply',
      waitForCompletion: true
    )
  }
  steps { echo 'Apply completed' }
}
```

An apply without a saved plan requires explicit `autoApprove: true`.

## Examples

- [asynchronous plan → await → apply](examples/Jenkinsfile.oci-resource-manager)
- [plan only](examples/Jenkinsfile.plan-only)
- [plan → apply](examples/Jenkinsfile.plan-apply)
- [asynchronous plan → await → apply](examples/Jenkinsfile.async-plan-apply)

## Asynchronous execution

With `waitForCompletion: false`, Jenkins submits the OCI job, stores the job OCID, and continues. A later `ociRmAwait` waits for the same job without requiring users to copy OCI job IDs into their Jenkinsfile.

## Restart and idempotency

OCI Resource Manager supports `opc-retry-token` for job creation. The plugin maps the core's persisted `requestToken` to that retry token and advertises idempotent submission support. This lets the core retry an interrupted submission without intentionally creating a duplicate job.

Operational reconciliation is still recommended after unusually long controller outages because provider retry-token retention is finite.

## Security

Use Jenkins Credentials for the API private key and optional passphrase. Do not place PEM keys, passphrases, or tenancy secrets in Pipeline source. See [SECURITY.md](SECURITY.md).

OCI IAM should grant only the Resource Manager and target-resource permissions needed by the stack.

## Compatibility

The plugin uses the OCI Java SDK directly and does not invoke the OCI CLI.

## Development

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

GitHub Actions uses a same-named core branch when present and otherwise falls back to core `main`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT License. See [LICENSE](LICENSE).
