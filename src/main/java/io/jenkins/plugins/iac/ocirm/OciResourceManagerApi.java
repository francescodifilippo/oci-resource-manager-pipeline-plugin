package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.*;
import com.oracle.bmc.resourcemanager.requests.CreateJobRequest;
import com.oracle.bmc.resourcemanager.requests.GetJobRequest;
import io.jenkins.plugins.iac.core.JobResult;

final class OciResourceManagerApi {
 private final ResourceManager client;
 OciResourceManagerApi(ResourceManager client){this.client=client;}
 static final String DESTROY_NEEDS_APPROVAL="mode: 'destroy' deletes every resource of the OCI stack without a reviewed plan; "+
   "set autoApprove: true in the ociRmProvision stage options to confirm";

 JobResult submit(String stackId,String mode,String displayName,String planJobId,boolean autoApprove,String retryToken){
  CreateJobDetails details=buildDetails(stackId,mode,displayName,planJobId,autoApprove);
  return OciJobResultMapper.from(client.createJob(CreateJobRequest.builder()
    .createJobDetails(details).opcRetryToken(retryToken).build()).getJob());
 }
 JobResult status(String jobId){
  return OciJobResultMapper.from(client.getJob(GetJobRequest.builder().jobId(jobId).build()).getJob());
 }

 static CreateJobDetails buildDetails(String stackId,String mode,String displayName,String planJobId,boolean autoApprove){
  CreateJobDetails.Builder details=CreateJobDetails.builder().stackId(stackId);
  if(displayName!=null&&!displayName.isBlank())details.displayName(displayName);
  switch(mode){
   case "plan" -> details.operation(Job.Operation.Plan)
      .jobOperationDetails(CreatePlanJobOperationDetails.builder().build());
   case "apply" -> {
    CreateApplyJobOperationDetails.Builder apply=CreateApplyJobOperationDetails.builder();
    if(planJobId!=null&&!planJobId.isBlank()){
     apply.executionPlanStrategy(ApplyJobOperationDetails.ExecutionPlanStrategy.FromPlanJobId)
       .executionPlanJobId(planJobId);
    }else{
     if(!autoApprove)throw new IllegalArgumentException("OCI apply requires planOperationKey/planJobId or autoApprove=true");
     apply.executionPlanStrategy(ApplyJobOperationDetails.ExecutionPlanStrategy.AutoApproved);
    }
    details.operation(Job.Operation.Apply).jobOperationDetails(apply.build());
   }
   case "destroy" -> {
    // OCI destroy jobs cannot be based on a reviewed plan: AutoApproved is the only strategy.
    if(!autoApprove)throw new IllegalArgumentException(DESTROY_NEEDS_APPROVAL);
    details.operation(Job.Operation.Destroy).jobOperationDetails(CreateDestroyJobOperationDetails.builder()
      .executionPlanStrategy(DestroyJobOperationDetails.ExecutionPlanStrategy.AutoApproved).build());
   }
   default -> throw new IllegalArgumentException("mode must be plan/apply/destroy");
  }
  return details.build();
 }
}
