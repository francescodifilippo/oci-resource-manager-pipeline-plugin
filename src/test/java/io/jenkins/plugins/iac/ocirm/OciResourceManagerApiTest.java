package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.*;
import org.junit.jupiter.api.Test;

class OciResourceManagerApiTest {
 @Test void buildsPlan(){
  CreateJobDetails x=OciResourceManagerApi.buildDetails("ocid1.ormstack.oc1..example","plan","jenkins-plan",null,false);
  assertEquals(Job.Operation.Plan,x.getOperation());
  assertInstanceOf(CreatePlanJobOperationDetails.class,x.getJobOperationDetails());
 }
 @Test void buildsApplyFromPlanJob(){
  CreateJobDetails x=OciResourceManagerApi.buildDetails("ocid1.ormstack.oc1..example","apply",null,"ocid1.ormjob.oc1..plan",false);
  assertEquals(Job.Operation.Apply,x.getOperation());
  CreateApplyJobOperationDetails op=(CreateApplyJobOperationDetails)x.getJobOperationDetails();
  assertEquals(ApplyJobOperationDetails.ExecutionPlanStrategy.FromPlanJobId,op.getExecutionPlanStrategy());
  assertEquals("ocid1.ormjob.oc1..plan",op.getExecutionPlanJobId());
 }
 @Test void applyWithoutPlanRequiresExplicitApproval(){
  assertThrows(IllegalArgumentException.class,()->OciResourceManagerApi.buildDetails(
   "ocid1.ormstack.oc1..example","apply",null,null,false));
  CreateJobDetails x=OciResourceManagerApi.buildDetails("ocid1.ormstack.oc1..example","apply",null,null,true);
  assertEquals(ApplyJobOperationDetails.ExecutionPlanStrategy.AutoApproved,
   ((CreateApplyJobOperationDetails)x.getJobOperationDetails()).getExecutionPlanStrategy());
 }
 @Test void buildsDestroy(){
  CreateJobDetails x=OciResourceManagerApi.buildDetails("ocid1.ormstack.oc1..example","destroy",null,null,false);
  assertEquals(Job.Operation.Destroy,x.getOperation());
  assertInstanceOf(CreateDestroyJobOperationDetails.class,x.getJobOperationDetails());
 }
}
