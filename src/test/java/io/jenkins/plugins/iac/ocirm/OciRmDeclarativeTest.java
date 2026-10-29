package io.jenkins.plugins.iac.ocirm;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Step validation as seen by Declarative users: stage options with agent none, failing before any OCI call. */
@WithJenkins
class OciRmDeclarativeTest {
 private static final String STACK="ocid1.ormstack.oc1..example";
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

 private WorkflowJob pipeline(String name,String stages) throws Exception {
  WorkflowJob p=j.createProject(WorkflowJob.class,name);
  p.setDefinition(new CpsFlowDefinition("pipeline {\n agent none\n stages {\n"+stages+"\n }\n}",true));
  return p;
 }
}
