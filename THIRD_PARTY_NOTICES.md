# Third-party libraries bundled in the HPI

The plugin is MIT licensed. Its HPI bundles these libraries in `WEB-INF/lib`, unchanged:

| Library | Version | License | Notices |
| --- | --- | --- | --- |
| `com.oracle.oci.sdk:oci-java-sdk-common` | 3.97.2 | UPL 1.0 or Apache 2.0 | `LICENSE.txt`, `NOTICE.txt`, `THIRD_PARTY_LICENSES.txt` in the jar |
| `com.oracle.oci.sdk:oci-java-sdk-common-httpclient` | 3.97.2 | UPL 1.0 or Apache 2.0 | same, in the jar |
| `com.oracle.oci.sdk:oci-java-sdk-common-httpclient-jersey` | 3.97.2 | UPL 1.0 or Apache 2.0 | same, in the jar |
| `com.oracle.oci.sdk:oci-java-sdk-circuitbreaker` | 3.97.2 | UPL 1.0 or Apache 2.0 | same, in the jar |
| `com.oracle.oci.sdk:oci-java-sdk-resourcemanager` | 3.97.2 | UPL 1.0 or Apache 2.0 | same, in the jar |
| `io.github.resilience4j:resilience4j-core` | 1.7.1 | Apache 2.0 | none published |
| `io.github.resilience4j:resilience4j-circuitbreaker` | 1.7.1 | Apache 2.0 | none published |
| `io.vavr:vavr` | 0.10.2 | Apache 2.0 | none published |
| `io.vavr:vavr-match` | 0.10.2 | Apache 2.0 | none published |

The OCI SDK also needs libraries that Jenkins already provides. The POM excludes them from the SDK dependencies, so
the HPI does not bundle a second copy that could conflict with them:

| Library | Provided by |
| --- | --- |
| Jersey 2, HK2, `jakarta.ws.rs-api`, Javassist | `jersey2-api` plugin |
| Jackson (core, databind, annotations, JSR-310) | `jackson2-api` plugin |
| Apache HttpClient 4 | `apache-httpcomponents-client-4-api` plugin |
| SLF4J, `javax.annotation-api`, `jakarta.annotation-api` | Jenkins core |

`OciRmClientTest` calls a local stub through the real SDK client of a connection, so that a missing library fails
the build. When the OCI SDK version changes, check the bundled list again:

```
mvn clean package -DskipTests
unzip -l target/oci-resource-manager-pipeline.hpi 'WEB-INF/lib/*'
```
