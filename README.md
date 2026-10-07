# OCI Resource Manager Pipeline

**Jenkins plugin ID:** `oci-resource-manager-pipeline`  
**Version:** `0.1.0-SNAPSHOT`

Declarative Jenkins Pipeline integration for **Oracle Cloud Infrastructure Resource Manager**. It implements the shared `iac-pipeline-api` backend contract used by the sibling OTF and Terrakube plugins.

## Features

- OCI Resource Manager `PLAN`, `APPLY`, and `DESTROY` jobs.
- Non-blocking Jenkins waiting through the shared IaC core.
- `waitForCompletion: false` plus a later `ociRmAwait`.
- Build-scoped correlation using `operationKey`.
- Apply from a previously completed plan using `planOperationKey` or a direct OCI `planJobId`.
- OCI `opc-retry-token` mapped from the core's persisted `requestToken`.
- OCI Java SDK; the plugin does not shell out to the OCI CLI.

## Jenkins configuration

In **Manage Jenkins → System**, configure an **OCI Resource Manager connection** with:

- connection name;
- OCI region, for example `eu-frankfurt-1`;
- tenancy OCID;
- user OCID;
- API key fingerprint;
- Jenkins **Secret Text** credential containing the PEM private key;
- optional Secret Text credential containing the private-key passphrase.

The Pipeline refers only to the connection name. Credentials are never persisted in the build's `RemoteOperation` metadata.

## Pipeline example

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
  steps { echo 'Plan complete' }
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
  steps { echo 'Apply complete' }
}
```

For an apply without a saved plan, `autoApprove: true` must be explicitly supplied. This is intentionally not the default.

A destroy job is explicit:

```groovy
ociRmProvision(
  connection: 'oci-prod',
  stackId: 'ocid1.ormstack.oc1.eu-frankfurt-1.example',
  mode: 'destroy',
  operationKey: 'oci-destroy',
  waitForCompletion: true
)
```

See `examples/Jenkinsfile.oci-resource-manager` for an asynchronous plan followed by await and apply.

## Restart/idempotency behavior

OCI Resource Manager accepts `opc-retry-token` when creating jobs. The plugin maps the durable core request token to that header and declares idempotent submit support, allowing the shared lifecycle to retry an interrupted submission. OCI documents retry-token expiry after 24 hours; very long controller outages should therefore still be reconciled operationally before a destructive retry.

## Build

Java 21 and Maven 3.9.6+:

```bash
(cd ../iac-pipeline-api-plugin && mvn -B -ntp install)
mvn -B -ntp verify
```

GitHub Actions checks out and installs `iac-pipeline-api-plugin` before building this provider.

## Status

Source prototype. Before a production release, add JenkinsRule/controller restart coverage and validate against real OCI Resource Manager stacks in a non-production tenancy.
