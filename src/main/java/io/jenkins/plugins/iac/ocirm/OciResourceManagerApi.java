package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.*;
import com.oracle.bmc.resourcemanager.requests.CreateJobRequest;
import com.oracle.bmc.resourcemanager.requests.GetJobRequest;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import com.oracle.bmc.resourcemanager.responses.ListStacksResponse;
import io.jenkins.plugins.iac.core.JobResult;
import java.util.ArrayList;
import java.util.List;

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

 /**
  * OCID of the one active stack of the compartment whose display name is exactly {@code name}. The service filter
  * is not trusted to be exact, so returned names are compared again; every page is read before counting.
  */
 String stackIdByName(String compartmentId,String name){
  List<String> ids=new ArrayList<>();
  String page=null;
  do{
   ListStacksResponse response=client.listStacks(ListStacksRequest.builder().compartmentId(compartmentId)
     .displayName(name).lifecycleState(Stack.LifecycleState.Active).page(page).build());
   if(response.getItems()!=null)for(StackSummary stack:response.getItems())
    if(name.equals(stack.getDisplayName()))ids.add(stack.getId());
   page=response.getOpcNextPage();
  }while(page!=null&&!page.isEmpty());
  if(ids.isEmpty())throw new IllegalArgumentException("No active OCI Resource Manager stack named '"+name+
    "' in compartment "+compartmentId+"; check stackName and compartmentId, or use stackId");
  if(ids.size()>1)throw new IllegalArgumentException(ids.size()+" active OCI Resource Manager stacks are named '"+name+
    "' in compartment "+compartmentId+" "+ids+"; use stackId");
  return ids.get(0);
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
