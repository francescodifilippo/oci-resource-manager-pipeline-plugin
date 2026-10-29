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
- Jenkins credential containing the PEM private key: preferably a **Secret file** (upload the `.pem` file); a **Secret text** credential is still accepted;
- optional Secret Text credential containing the private-key passphrase.

Only administrators can list credentials in the connection form.

The Secret text field is a single-line input, so a pasted PEM loses its line breaks. The plugin restores them when the value is a plain `-----BEGIN …-----` / `-----END …-----` block with a base64 body. Legacy encrypted PEM files with `Proc-Type`/`DEK-Info` headers cannot be restored this way: store them as a Secret file.

Pipeline code refers only to the connection name. Private keys and passphrases are never persisted in `RemoteOperation` and never written to the build log.

## Pipeline syntax

### `ociRmProvision`

| Parameter | Purpose |
| --- | --- |
| `connection` | configured OCI connection |
| `stackId` | Resource Manager stack OCID; set either `stackId` or `stackName` |
| `stackName` | exact display name of an active stack, looked up when the stage runs (see [Stack names](#stack-names)) |
| `compartmentId` | OCID of the compartment that contains the stack; required with `stackName`, not allowed with `stackId` |
| `mode` | `plan`, `apply`, or `destroy` |
| `displayName` | optional OCI job display name |
| `planOperationKey` | apply a successful plan from the same Jenkins build: the plan stage's explicit `operationKey` |
| `planJobId` | direct OCI plan job OCID alternative |
| `autoApprove` | explicitly permit apply without a prior plan; required for `destroy` |
| `operationKey` | build-local key of this operation, unique per provision step in a build; set it whenever another stage refers to this operation |
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

## Stack names

A stage can name its stack instead of giving its OCID:

```groovy
stage('Plan') {
  options {
    ociRmProvision(
      connection: 'oci-prod',
      stackName: 'network',
      compartmentId: 'ocid1.compartment.oc1..example',
      mode: 'plan',
      operationKey: 'oci-plan'
    )
  }
  steps { echo 'Plan completed' }
}
```

- Set exactly one of `stackId` or `stackName`. `stackName` requires `compartmentId`, and `compartmentId` is rejected with `stackId`, so a stage never carries a compartment that is not used. These checks run when the stage starts, before any OCI call.
- When the stage runs, the plugin lists the `ACTIVE` stacks of that compartment (not its sub-compartments) whose display name is exactly `stackName`, with the stage's connection. Names are case-sensitive. No match, or more than one, fails the stage before any job is created; the message gives the number of matches and, for several, their OCIDs. Use `stackId` to pick one of several stacks with the same name.
- The build log shows the resolved stack, for example `IaC oci-rm: resolved target to ocid1.ormstack.oc1...`.
- Everything after the lookup uses the stack OCID: the stored operation, the OCI job, and the `planOperationKey` check that the plan belongs to the same stack. A plan by `stackName` and an apply by `stackId` of the same stack therefore work together, while an apply on another stack fails.
- The lookup runs once per `ociRmProvision` stage. `ociRmAwait` refers to an operation by `operationKey` and never looks up names.
- A stage with `stackId` makes no lookup call and behaves as before.

The lookup needs permission to list stacks in the compartment, for example:

```text
Allow group <group> to inspect orm-stacks in compartment <compartment>
```

`read orm-stacks` or `manage orm-stacks` also include it. Without it, OCI rejects the request (typically with `NotAuthorizedOrNotFound`) and the stage fails.

See [examples/Jenkinsfile.by-name](examples/Jenkinsfile.by-name).

## Operation keys

Each `ociRmProvision` step records its OCI job in the build under an `operationKey`. `planOperationKey` and `ociRmAwait` find an earlier job by that key, so the referenced stage must set an explicit `operationKey`, as in all examples.

Without `operationKey`, the step derives a default key from the provider (`oci-rm`), the stack and the job parameters (`mode`, `displayName`, `planOperationKey`, `planJobId`, `autoApprove`; with `stackName`, also `stackName` and `compartmentId` instead of the stack OCID), so a plan and an apply on the same stack get different keys. Default keys are not meant to be written in a Jenkinsfile: do not reference them.

A key can be used by only one provision step per build. A second step with the same key, including the same step re-run inside `retry`, fails with an error asking for a distinct `operationKey`.

## Destroy

OCI Resource Manager destroy jobs cannot be based on a reviewed plan: they always delete every resource of the stack. The step therefore refuses `mode: 'destroy'` unless the stage options also set `autoApprove: true`, and fails before contacting OCI:

```groovy
stage('Destroy') {
  options {
    ociRmProvision(
      connection: 'oci-prod',
      stackId: 'ocid1.ormstack.oc1.eu-frankfurt-1.example',
      mode: 'destroy',
      autoApprove: true,
      operationKey: 'oci-destroy'
    )
  }
  steps { echo 'Destroy completed' }
}
```

Guard such a stage with an `input` step or a `when` condition if the stack must not be destroyed by accident.

## Examples

- [asynchronous plan with display name → await → apply](examples/Jenkinsfile.oci-resource-manager)
- [plan only](examples/Jenkinsfile.plan-only)
- [plan → apply](examples/Jenkinsfile.plan-apply)
- [asynchronous plan → await → apply](examples/Jenkinsfile.async-plan-apply)
- [destroy with explicit approval](examples/Jenkinsfile.destroy)
- [plan → apply with the stack named by `stackName` and `compartmentId`](examples/Jenkinsfile.by-name)

## Asynchronous execution

With `waitForCompletion: false`, Jenkins submits the OCI job, stores the job OCID, and continues. A later `ociRmAwait` waits for the same job without requiring users to copy OCI job IDs into their Jenkinsfile.

## Restart and idempotency

OCI Resource Manager supports `opc-retry-token` for job creation. The plugin maps the core's persisted `requestToken` to that retry token and advertises idempotent submission support. This lets the core retry an interrupted submission without intentionally creating a duplicate job.

While waiting, a failed job-status request is retried on the next poll, up to five consecutive times, when OCI reports a server error (5xx), throttling (429), a request timeout (408), or a network/client-side timeout. Other errors, such as authorization failures or a missing credential, fail the stage immediately.

Operational reconciliation is still recommended after unusually long controller outages because provider retry-token retention is finite.

## Security

Use Jenkins Credentials for the API private key and optional passphrase. Do not place PEM keys, passphrases, or tenancy secrets in Pipeline source. See [SECURITY.md](SECURITY.md).

OCI IAM should grant only the Resource Manager and target-resource permissions needed by the stack. Stages that use `stackName` also need `inspect orm-stacks` in the stack's compartment.

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
