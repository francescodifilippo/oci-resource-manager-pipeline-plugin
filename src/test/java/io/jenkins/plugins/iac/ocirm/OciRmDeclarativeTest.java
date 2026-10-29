package io.jenkins.plugins.iac.ocirm;

import static io.jenkins.plugins.iac.ocirm.FakeResourceManager.stack;
import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.CreateApplyJobOperationDetails;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import hudson.ExtensionList;
import hudson.model.Result;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Stage options as seen by Declarative users, with agent none. Without a {@link FakeResourceManager} installed,
 * any OCI call fails at the (unconfigured) connection.
 */
@WithJenkins
class OciRmDeclarativeTest {
 private static final String STACK="ocid1.ormstack.oc1..example";
 private static final String COMPARTMENT="ocid1.compartment.oc1..example";
 private static final String NETWORK="ocid1.ormstack.oc1..network",DATABASE="ocid1.ormstack.oc1..database";
 private JenkinsRule j;

 @BeforeEach void setUp(JenkinsRule rule){j=rule;}

 @Test void destroyWithoutAutoApproveFailsBeforeAnyOciCall() throws Exception {
  // No connection is configured: reaching the OCI client would fail with "Unknown OCI Resource Manager connection".
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("destroy",
    "stage('destroy') {\n options { ociRmProvision(connection: 'oci', stackId: '"+STACK+"', mode: 'destroy') }\n steps { echo 'destroy body' }\n}"));
  j.assertLogContains("set autoApprove: true in the ociRmProvision stage options",b);
  j.assertLogNotContains("Unknown OCI Resource Manager connection",b);
  j.assertLogNotContains("submitting target",b);
  j.assertLogNotContains("destroy body",b);
 }

 @Test void destroyWithAutoApproveReachesTheConnection() throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("destroy-approved",
    "stage('destroy') {\n options { ociRmProvision(connection: 'oci', stackId: '"+STACK+"', mode: 'destroy', autoApprove: true, operationKey: 'oci-destroy') }\n steps { echo 'destroy body' }\n}"));
  j.assertLogContains("Unknown OCI Resource Manager connection: oci",b);
  j.assertLogNotContains("set autoApprove: true",b);
  j.assertLogNotContains("destroy body",b);
 }

 @Test void stackNameWithCompartmentReachesTheConnection() throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("by-name",
    "stage('plan') {\n options { ociRmProvision(connection: 'oci', stackName: 'network', compartmentId: '"+COMPARTMENT+"') }\n steps { echo 'plan body' }\n}"));
  j.assertLogContains("Unknown OCI Resource Manager connection: oci",b);
  j.assertLogNotContains("plan body",b);
 }

 @Test void planByNameThenApplyByStackIdOfTheSameStack() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(NETWORK,"network"),stack(DATABASE,"database")));
  WorkflowRun b=j.buildAndAssertSuccess(pipeline("plan-name-apply-id",
    planByName("network")+applyWith("stackId: '"+NETWORK+"'")));
  j.assertLogContains("IaC oci-rm: resolved target to "+NETWORK,b);
  j.assertLogContains("apply body",b);
  assertEquals(1,fake.listRequests.size());
  assertEquals(2,fake.createRequests.size());
  var apply=fake.createRequests.get(1).getCreateJobDetails();
  assertEquals(NETWORK,apply.getStackId());
  assertEquals("ocid1.ormjob.oc1..job1",((CreateApplyJobOperationDetails)apply.getJobOperationDetails()).getExecutionPlanJobId());
 }
 @Test void planAndApplyByName() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(NETWORK,"network"),stack(DATABASE,"database")));
  j.buildAndAssertSuccess(pipeline("plan-apply-name",
    planByName("network")+applyWith("stackName: 'network', compartmentId: '"+COMPARTMENT+"'")));
  assertEquals(2,fake.listRequests.size());
  assertEquals(NETWORK,fake.createRequests.get(1).getCreateJobDetails().getStackId());
 }
 @Test void applyOfAPlanForAnotherStackFails() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(NETWORK,"network"),stack(DATABASE,"database")));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("plan-name-apply-other",
    planByName("network")+applyWith("stackId: '"+DATABASE+"'")));
  j.assertLogContains("Plan oci-plan belongs to a different OCI stack",b);
  j.assertLogNotContains("apply body",b);
  assertEquals(1,fake.createRequests.size());
 }
 @Test void ambiguousStackNameFailsBeforeSubmitting() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(NETWORK,"network"),stack(DATABASE,"network")));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("ambiguous",planByName("network")));
  j.assertLogContains("2 active OCI Resource Manager stacks are named 'network' in compartment "+COMPARTMENT,b);
  j.assertLogContains(NETWORK,b);
  j.assertLogContains(DATABASE,b);
  j.assertLogNotContains("submitting target",b);
  j.assertLogNotContains("plan body",b);
  assertEquals(0,fake.createRequests.size());
 }
 @Test void unknownStackNameFailsBeforeSubmitting() throws Exception {
  FakeResourceManager fake=fakeOci(List.of(stack(NETWORK,"network")));
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline("unknown",planByName("Network")));
  j.assertLogContains("No active OCI Resource Manager stack named 'Network' in compartment "+COMPARTMENT,b);
  j.assertLogNotContains("plan body",b);
  assertEquals(0,fake.createRequests.size());
 }

 @Test void stackIdOrStackNameIsRequired() throws Exception {
  assertRejected("no-stack","connection: 'oci'","ociRmProvision needs stackId, or stackName with compartmentId");
 }
 @Test void stackIdAndStackNameAreExclusive() throws Exception {
  assertRejected("both","connection: 'oci', stackId: '"+STACK+"', stackName: 'network', compartmentId: '"+COMPARTMENT+"'",
    "Use either stackId or stackName in the ociRmProvision stage options, not both");
 }
 @Test void stackNameNeedsCompartmentId() throws Exception {
  assertRejected("no-compartment","connection: 'oci', stackName: 'network'","stackName needs compartmentId");
 }
 @Test void compartmentIdIsRejectedWithStackId() throws Exception {
  assertRejected("stray-compartment","connection: 'oci', stackId: '"+STACK+"', compartmentId: '"+COMPARTMENT+"'",
    "compartmentId is used only with stackName");
 }

 private FakeResourceManager fakeOci(List<StackSummary> stacks){
  FakeResourceManager fake=FakeResourceManager.withPages(stacks);
  ExtensionList.lookupSingleton(OciRmBackend.class).clients=(connection,run)->fake.client();
  return fake;
 }
 private static String planByName(String name){
  return "stage('plan') {\n options { ociRmProvision(connection: 'oci', stackName: '"+name+"', compartmentId: '"+COMPARTMENT+
    "', operationKey: 'oci-plan') }\n steps { echo 'plan body' }\n}\n";
 }
 private static String applyWith(String stack){
  return "stage('apply') {\n options { ociRmProvision(connection: 'oci', "+stack+
    ", mode: 'apply', planOperationKey: 'oci-plan', operationKey: 'oci-apply') }\n steps { echo 'apply body' }\n}\n";
 }
 private void assertRejected(String name,String args,String message) throws Exception {
  WorkflowRun b=j.buildAndAssertStatus(Result.FAILURE,pipeline(name,
    "stage('plan') {\n options { ociRmProvision("+args+") }\n steps { echo 'plan body' }\n}"));
  j.assertLogContains(message,b);
  j.assertLogNotContains("Unknown OCI Resource Manager connection",b);
  j.assertLogNotContains("submitting target",b);
  j.assertLogNotContains("plan body",b);
 }

 private WorkflowJob pipeline(String name,String stages) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class,name);
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n"+stages+"\n }\n}",true));
  return p;
 }
}
