package io.jenkins.plugins.iac.ocirm;

import static io.jenkins.plugins.iac.ocirm.FakeResourceManager.stack;
import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.CreateGitConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateObjectStorageConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateStackDetails;
import com.oracle.bmc.resourcemanager.model.Stack.LifecycleState;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import hudson.model.Result;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * ociRmStack as a Declarative stage option, with agent none. Without a {@link FakeResourceManager} installed, any
 * OCI call fails at the (unconfigured) connection.
 */
@WithJenkins
class OciRmStackDeclarativeTest {
 private static final String COMPARTMENT="ocid1.compartment.oc1..example";
 private static final String PROVIDER="ocid1.ormconfigsourceprovider.oc1..github";
 private static final String REPO="https://github.com/example/network.git";
 private static final String EXISTING="ocid1.ormstack.oc1..existing",OTHER="ocid1.ormstack.oc1..other";
 private static final String CREATED="ocid1.ormstack.oc1..created1";
 private static final String GIT="configurationSourceProviderId: '"+PROVIDER+"', repositoryUrl: '"+REPO+"'";
 private JenkinsRule j;

 @BeforeEach void setUp(JenkinsRule rule){j=rule;}
 @AfterEach void restoreClients(){OciRmBackend.clients=OciRmBackend.CONNECTION_CLIENTS;}

 @Test void createsAStackFromGitAndWaitsUntilActive() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  fake.createdStates=List.of(LifecycleState.Creating,LifecycleState.Active);
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("git",stage(GIT+", branchName: 'main', workingDirectory: 'stacks/network', "+
    "description: 'Network', terraformVersion: '1.5.x', variables: [region: 'eu-frankfurt-1', node_count: 3, ha: true, "+
    "label: \"net-${1+1}\"], pollingSeconds: 5")));
  assertEquals(1,fake.createStackRequests.size());
  var request=fake.createStackRequests.get(0);
  assertNotNull(request.getOpcRetryToken());
  CreateStackDetails details=request.getCreateStackDetails();
  assertEquals(COMPARTMENT,details.getCompartmentId());
  assertEquals("network",details.getDisplayName());
  assertEquals("Network",details.getDescription());
  assertEquals("1.5.x",details.getTerraformVersion());
  assertEquals(Map.of("region","eu-frankfurt-1","node_count","3","ha","true","label","net-2"),details.getVariables());
  CreateGitConfigSourceDetails source=(CreateGitConfigSourceDetails)details.getConfigSource();
  assertEquals(PROVIDER,source.getConfigurationSourceProviderId());
  assertEquals(REPO,source.getRepositoryUrl());
  assertEquals("main",source.getBranchName());
  assertEquals("stacks/network",source.getWorkingDirectory());
  j.assertLogContains("IaC oci-rm: creating stack 'network' in compartment "+COMPARTMENT+" from Git repository "+REPO+
    " with variables [region, node_count, ha, label]",b);
  j.assertLogContains("created stack "+CREATED+" state CREATING",b);
  j.assertLogContains("stack "+CREATED+" is ACTIVE",b);
  j.assertLogNotContains("eu-frankfurt-1",b);
  j.assertLogContains("stack body",b);
 }

 @Test void objectStorageSourceDefaultsToTheConnectionRegion() throws Exception {
  OciRmConnections.get().setConnections(List.of(
    new OciRmConnection("oci","eu-milan-1","ocid1.tenancy.oc1..t","ocid1.user.oc1..u","aa:bb","oci-key")));
  FakeResourceManager fake=fakeOci(List.of());
  j.buildAndAssertSuccess(pipeline("bucket",stage("bucketName: 'tf', namespace: 'ns'")));
  var source=(CreateObjectStorageConfigSourceDetails)fake.createStackRequests.get(0).getCreateStackDetails().getConfigSource();
  assertEquals("tf",source.getBucketName());
  assertEquals("ns",source.getNamespace());
  assertEquals("eu-milan-1",source.getRegion());
  assertNull(fake.createStackRequests.get(0).getCreateStackDetails().getVariables());
 }

 @Test void objectStorageSourceWithItsOwnRegion() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("bucket-region",stage("bucketName: 'tf', namespace: 'ns', region: 'us-ashburn-1'")));
  var source=(CreateObjectStorageConfigSourceDetails)fake.createStackRequests.get(0).getCreateStackDetails().getConfigSource();
  assertEquals("us-ashburn-1",source.getRegion());
  j.assertLogContains("from Object Storage bucket tf",b);
 }

 @Test void keepsAnExistingActiveStackUnchanged() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network",LifecycleState.Active),stack(OTHER,"database",LifecycleState.Active)));
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("keep",stage(GIT)));
  assertEquals(0,fake.createStackRequests.size());
  j.assertLogContains("found existing stack "+EXISTING+" state ACTIVE; configuration not compared or changed",b);
  j.assertLogContains("stack body",b);
 }

 @Test void waitsForAnExistingStackThatIsStillCreating() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network")))
    .script(EXISTING,LifecycleState.Creating,LifecycleState.Active);
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("creating",stage(GIT+", pollingSeconds: 5")));
  assertEquals(0,fake.createStackRequests.size());
  j.assertLogContains("found existing stack "+EXISTING+" state CREATING",b);
  j.assertLogContains("stack "+EXISTING+" is ACTIVE",b);
  j.assertLogContains("stack body",b);
 }

 @Test void deletedStacksAreIgnored() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network",LifecycleState.Deleted),stack(OTHER,"network",LifecycleState.Deleting)));
  j.buildAndAssertSuccess(pipeline("deleted",stage(GIT)));
  assertEquals(1,fake.createStackRequests.size());
 }

 @Test void anExistingFailedStackFails() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network",LifecycleState.Failed)));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("failed",stage(GIT)));
  j.assertLogContains("OCI Resource Manager stack "+EXISTING+" is FAILED",b);
  j.assertLogNotContains("stack body",b);
  assertEquals(0,fake.createStackRequests.size());
 }

 @Test void ifExistsFailRejectsAnExistingStack() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network",LifecycleState.Active)));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("if-exists-fail",stage(GIT+", ifExists: 'fail'")));
  j.assertLogContains("OCI Resource Manager stack 'network' in compartment "+COMPARTMENT+" already exists: "+EXISTING,b);
  j.assertLogNotContains("stack body",b);
  assertEquals(0,fake.createStackRequests.size());
 }

 @Test void ifExistsFailCreatesAMissingStack() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  j.buildAndAssertSuccess(pipeline("if-exists-fail-new",stage(GIT+", ifExists: 'fail'")));
  assertEquals(1,fake.createStackRequests.size());
 }

 @Test void severalStacksWithTheNameFail() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(EXISTING,"network",LifecycleState.Active),stack(OTHER,"network",LifecycleState.Creating)));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("ambiguous",stage(GIT)));
  j.assertLogContains("2 OCI Resource Manager stacks are named 'network' in compartment "+COMPARTMENT+" ["+EXISTING+", "+OTHER+"]",b);
  j.assertLogNotContains("stack body",b);
  assertEquals(0,fake.createStackRequests.size());
 }

 @Test void aStackCreatedConcurrentlyWithTheSameNameFails() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  fake.concurrent.add(stack(OTHER,"network",LifecycleState.Creating));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("race",stage(GIT)));
  j.assertLogContains("Another stack named 'network' was created in compartment "+COMPARTMENT+" at the same time: ["+CREATED+", "+OTHER+"]",b);
  j.assertLogNotContains("stack body",b);
 }

 @Test void aConcurrentStackFailsEvenWhenTheListDoesNotShowTheNewStackYet() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  fake.hideCreated=true;
  fake.concurrent.add(stack(OTHER,"network",LifecycleState.Active));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("race-hidden",stage(GIT)));
  j.assertLogContains("at the same time: ["+CREATED+", "+OTHER+"]",b);
  j.assertLogNotContains("stack body",b);
 }

 @Test void aCreatedStackThatFailsFailsTheStage() throws Exception {
  FakeResourceManager fake=fakeOci(List.of());
  fake.createdStates=List.of(LifecycleState.Creating,LifecycleState.Failed);
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("create-failed",stage(GIT+", pollingSeconds: 5")));
  j.assertLogContains("OCI Resource Manager stack "+CREATED+" is FAILED",b);
  j.assertLogNotContains("stack body",b);
 }

 @Test void aCreatedStackCanBeUsedByNameInALaterStage() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(OTHER,"database",LifecycleState.Active)));
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("stack-then-plan",stage(GIT)+
    "stage('plan') {\n options { ociRmProvision(connection: 'oci', stackName: 'network', compartmentId: '"+COMPARTMENT+
    "', operationKey: 'oci-plan') }\n steps { echo 'plan body' }\n}\n"));
  j.assertLogContains("IaC oci-rm: resolved target to "+CREATED,b);
  assertEquals(CREATED,fake.createRequests.get(0).getCreateJobDetails().getStackId());
  j.assertLogContains("plan body",b);
 }

 @Test void aConfigurationSourceIsRequired() throws Exception {
  assertRejected("no-source","","ociRmStack needs a configuration source");
 }
 @Test void gitAndObjectStorageAreExclusive() throws Exception {
  assertRejected("both-sources",", "+GIT+", bucketName: 'tf', namespace: 'ns'","Use either a Git source");
 }
 @Test void gitNeedsARepositoryUrl() throws Exception {
  assertRejected("no-repo",", configurationSourceProviderId: '"+PROVIDER+"'",
    "A Git source needs both configurationSourceProviderId and repositoryUrl");
 }
 @Test void objectStorageNeedsANamespace() throws Exception {
  assertRejected("no-namespace",", bucketName: 'tf'","An Object Storage source needs both bucketName and namespace");
 }
 @Test void ifExistsIsChecked() throws Exception {
  assertRejected("bad-if-exists",", "+GIT+", ifExists: 'update'","ifExists must be keep or fail");
 }
 @Test void variableValuesMustBeScalars() throws Exception {
  assertRejected("bad-variable",", "+GIT+", variables: [zones: ['a', 'b']]","ociRmStack variable zones must be a string, number or boolean");
 }

 private FakeResourceManager fakeOci(List<StackSummary> stacks){
  FakeResourceManager fake=FakeResourceManager.withPages(stacks);
  OciRmBackend.clients=(connection,run)->fake.client();
  return fake;
 }
 private static String stage(String source){
  return "stage('stack') {\n options { ociRmStack(connection: 'oci', stackName: 'network', compartmentId: '"+COMPARTMENT+"', "+
    source+") }\n steps { echo 'stack body' }\n}\n";
 }
 private void assertRejected(String name,String args,String message) throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(name,
    "stage('stack') {\n options { ociRmStack(connection: 'oci', stackName: 'network', compartmentId: '"+COMPARTMENT+"'"+args+
    ") }\n steps { echo 'stack body' }\n}"));
  j.assertLogContains(message,b);
  j.assertLogNotContains("Unknown OCI Resource Manager connection",b);
  j.assertLogNotContains("creating stack",b);
  j.assertLogNotContains("stack body",b);
 }
 private WorkflowJob pipeline(String name,String stages) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class,name);
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n"+stages+"\n }\n}",true));
  return p;
 }
}
