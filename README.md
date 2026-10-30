# OCI Resource Manager Pipeline

**Plugin ID:** `oci-resource-manager-pipeline`  
**Status:** pre-release

Declarative Jenkins Pipeline integration for Oracle Cloud Infrastructure Resource Manager. It uses the OCI Java SDK and the shared [IaC Pipeline API](https://github.com/francescodifilippo/iac-pipeline-api-plugin) to run `PLAN`, `APPLY`, and `DESTROY` jobs with durable asynchronous execution, and can create the stacks those jobs run on.

## Requirements

- Jenkins 2.568.3 or newer
- Java 21
- `iac-pipeline-api` of the matching tested version
- OCI Resource Manager stack, or a Git or Object Storage configuration source to create one with `ociRmStack`
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

### `ociRmStack`

Finds a stack by name, or creates it, and runs the stage once the stack is `ACTIVE`. It runs no job. See [Stack creation](#stack-creation).

| Parameter | Purpose |
| --- | --- |
| `connection` | configured OCI connection; the stack is created in its tenancy |
| `stackName` | exact display name of the stack |
| `compartmentId` | OCID of the compartment that contains the stack |
| `configurationSourceProviderId` | Git source: OCID of an existing Resource Manager configuration source provider |
| `repositoryUrl` | Git source: repository URL |
| `branchName` | Git source: optional branch; the repository's default branch otherwise |
| `bucketName` | Object Storage source: bucket that contains the Terraform configuration |
| `namespace` | Object Storage source: Object Storage namespace of the bucket |
| `region` | Object Storage source: optional bucket region; the connection's region otherwise |
| `workingDirectory` | optional directory of the Terraform configuration inside the source |
| `terraformVersion` | optional Terraform version, for example `1.5.x` |
| `description` | optional stack description |
| `variables` | optional map of plain-text Terraform variables; never secrets |
| `ifExists` | `keep` (default) or `fail` when the stack already exists |
| `pollingSeconds` | polling interval while the stack is `CREATING` (default 10) |
| `timeoutMinutes` | maximum wait for `ACTIVE` (default 30) |

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

## Stack creation

Each Resource Manager only manages its own tenancy. `ociRmStack` lets one Jenkins create stacks in several tenancies: configure one connection per tenancy and region, and choose the connection in the stage.

```groovy
stage('Stack') {
  options {
    ociRmStack(
      connection: 'oci-tenant-a',
      stackName: 'network',
      compartmentId: 'ocid1.compartment.oc1..example',
      configurationSourceProviderId: 'ocid1.ormconfigsourceprovider.oc1.eu-frankfurt-1.example',
      repositoryUrl: 'https://github.com/example/infrastructure.git',
      branchName: 'main',
      workingDirectory: 'stacks/network',
      variables: [vcn_cidr: '10.0.0.0/16', subnet_count: 3]
    )
  }
  steps { echo 'Stack is active' }
}
```

A later stage runs jobs on the stack with `ociRmProvision(stackName: 'network', compartmentId: ...)`, as described in [Stack names](#stack-names). See [examples/Jenkinsfile.create-stack](examples/Jenkinsfile.create-stack).

### Configuration source

Set exactly one source:

- **Git:** `configurationSourceProviderId` and `repositoryUrl`, optionally `branchName`. The configuration source provider must already exist in OCI. The plugin does not create providers and never sends Git tokens to OCI.
- **Object Storage:** `bucketName` and `namespace`, optionally `region`.

`workingDirectory` applies to both sources. A zip upload is not supported: with `agent none` there is no workspace to read a file from.

### Existing stacks

When the stage runs, the plugin lists the stacks of the compartment (not its sub-compartments) whose display name is exactly `stackName`. Names are case-sensitive. Stacks that are `DELETING` or `DELETED` are ignored.

- No stack: the plugin creates one and waits until it is `ACTIVE`.
- One `ACTIVE` stack: with `ifExists: 'keep'` (default) the stage uses it. Its configuration and variables are not compared or changed. With `ifExists: 'fail'` the stage fails.
- One `CREATING` stack: the stage waits for it instead of creating another.
- One `FAILED` stack: the stage fails and shows its OCID. Delete the stack in OCI before running the stage again.
- More than one stack: the stage fails and lists their OCIDs.

The build log shows the stack OCID, for example `IaC oci-rm: created stack ocid1.ormstack.oc1...`.

### Variables

Stack variables are stored in plain text in Resource Manager, and anyone with `read orm-stacks` on the compartment can read them. Pass only non-secret values in `variables`. Numbers and booleans are turned into strings, as Resource Manager expects. The build log shows the variable names, never their values.

### Concurrent builds and restarts

OCI allows several stacks with the same name. If two builds create the same stack at the same time, the plugin lists the stacks again after creation and fails when it finds more than one. Prevent this with `disableConcurrentBuilds()` or a `lock` around the stage.

The step holds no executor while it waits. Before the create request it saves an `opc-retry-token` with the build. After a Jenkins restart the step repeats the request with the same token, so that OCI returns the stack it already created instead of creating a second one. A restart before the request simply looks the stack up again.

OCI keeps retry tokens for a limited time. After an unusually long controller outage, a repeated request may create a second stack; the check after creation then finds two stacks with the name and fails the stage, and the extra stack must be deleted by hand.

### Permissions

Creating a stack needs `manage orm-stacks` in the compartment. A Git source also needs `read orm-config-source-providers` in the compartment of the configuration source provider:

```text
Allow group <group> to manage orm-stacks in compartment <compartment>
Allow group <group> to read orm-config-source-providers in compartment <provider-compartment>
```

For an Object Storage source, the Oracle Resource Manager policy reference names no bucket permission. Verify the required Object Storage access in your tenancy.

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
- [stack creation from Git → plan by stack name](examples/Jenkinsfile.create-stack)
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

OCI IAM should grant only the Resource Manager and target-resource permissions needed by the stack. Stages that use `stackName` also need `inspect orm-stacks` in the stack's compartment. `ociRmStack` needs `manage orm-stacks` (see [Permissions](#permissions)); grant it only to connections whose pipelines create stacks.

Stack `variables` are visible in plain text in OCI. Never put secrets in them.

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
