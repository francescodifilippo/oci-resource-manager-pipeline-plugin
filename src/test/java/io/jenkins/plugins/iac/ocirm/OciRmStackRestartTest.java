package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;

import com.oracle.bmc.resourcemanager.model.Stack.LifecycleState;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/** ociRmStack when Jenkins restarts during the create request or while the stack is CREATING. */
class OciRmStackRestartTest {
 private static final String CREATED="ocid1.ormstack.oc1..created1";
 @RegisterExtension
 private final JenkinsSessionExtension sessions=new JenkinsSessionExtension();
 private FakeResourceManager fake;

 @BeforeEach void installFake(){
  fake=FakeResourceManager.withPages(List.of());
  // Static, so that the resumed build of the second session uses the same fake.
  OciRmBackend.clients=(connection,run)->fake.client();
 }
 @AfterEach void restoreClients(){OciRmBackend.clients=OciRmBackend.CONNECTION_CLIENTS;}

 @Test void aRestartWhileCreatingKeepsPollingTheCreatedStack() throws Throwable {
  fake.createdStates=Collections.nCopies(1000,LifecycleState.Creating);
  sessions.then(j -> {
   WorkflowRun b=start(j);
   j.waitForMessage("created stack "+CREATED+" state CREATING",b);
  });
  fake.script(CREATED,LifecycleState.Active);
  sessions.then(j -> {
   WorkflowRun b=resumed(j);
   assertEquals(1,fake.createStackRequests.size());
   j.assertLogContains("stack "+CREATED+" is ACTIVE",b);
  });
 }

 @Test void aRestartDuringTheCreateRequestRepeatsItWithTheSameRetryToken() throws Throwable {
  fake.holdCreate=true;
  sessions.then(j -> {
   start(j);
   assertTrue(fake.held.await(1,TimeUnit.MINUTES),"createStack was not called");
  });
  sessions.then(j -> {
   WorkflowRun b=resumed(j);
   j.assertLogContains("repeating the interrupted create request for stack 'network'",b);
   assertEquals(2,fake.createStackRequests.size());
   assertEquals(fake.createStackRequests.get(0).getOpcRetryToken(),fake.createStackRequests.get(1).getOpcRetryToken());
   assertEquals(List.of(CREATED),fake.createdIds());
   j.assertLogContains("stack "+CREATED+" is ACTIVE",b);
  });
 }

 private static WorkflowRun start(JenkinsRule j) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class,"stack");
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n stage('stack') {\n options { ociRmStack("+
    "connection: 'oci', stackName: 'network', compartmentId: 'ocid1.compartment.oc1..example', "+
    "configurationSourceProviderId: 'ocid1.ormconfigsourceprovider.oc1..github', "+
    "repositoryUrl: 'https://github.com/example/network.git', pollingSeconds: 5) }\n"+
    " steps { echo 'stack body' }\n }\n }\n}",true));
  return p.scheduleBuild2(0).waitForStart();
 }
 /** The build of the first session, completed after the restart, with the body run once. */
 private static WorkflowRun resumed(JenkinsRule j) throws Exception {
  WorkflowRun b=j.jenkins.getItemByFullName("stack",WorkflowJob.class).getBuildByNumber(1);
  j.assertBuildStatusSuccess(j.waitForCompletion(b));
  String log=JenkinsRule.getLog(b);
  assertTrue(log.contains("Resuming build"),log);
  assertEquals(1,log.split(Pattern.quote("stack body"),-1).length-1,log);
  return b;
 }
}
