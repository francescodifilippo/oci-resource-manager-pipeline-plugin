package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.AsyncBlockExecution;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.workflow.steps.StepContext;

/**
 * Finds or creates the stack of an {@code ociRmStack} step, polling until it is ACTIVE, then lets the body run.
 * Safe to repeat after a restart: before the create request the execution persists its retry token, and a resumed
 * execution repeats that same request, which OCI answers with the stack already created.
 */
final class OciRmStackExecution extends AsyncBlockExecution {
 private static final long serialVersionUID=1L;
 /** Consecutive transient stack-status failures tolerated before the step fails, as for jobs. */
 static final int MAX_TRANSIENT_STATUS_FAILURES=5;
 private final String connection;
 private final OciRmStackSpec spec;
 private final boolean failIfExists;
 private final int pollingSeconds,timeoutMinutes;
 private final String retryToken=UUID.randomUUID().toString();
 /** Set and saved before the create request; from then on only that request (same retry token) is repeated. */
 private boolean createRequested;
 /** True when this execution created the stack, so that the name is checked again for a concurrent duplicate. */
 private boolean created;
 private String stackId;
 private int transientStatusFailures;

 OciRmStackExecution(StepContext context,String connection,OciRmStackSpec spec,boolean failIfExists,int pollingSeconds,int timeoutMinutes){
  super(context,"oci-rm");this.connection=connection;this.spec=spec;this.failIfExists=failIfExists;
  this.pollingSeconds=pollingSeconds;this.timeoutMinutes=timeoutMinutes;
 }
 @Override protected int pollingSeconds(){return pollingSeconds;}
 private String describe(){return "stack '"+spec.stackName+"' in compartment "+spec.compartmentId;}

 @Override protected boolean advance() throws Exception {
  Run<?,?> run=getContext().get(Run.class);
  try(ResourceManager client=OciRmBackend.clients.apply(connection,run)){
   OciResourceManagerApi api=new OciResourceManagerApi(client);
   if(stackId==null&&!createRequested){
    StackSummary existing=findExisting(api);
    if(existing!=null)return ready(api,existing.getLifecycleState());
   }
   Stack stack;
   if(stackId==null){
    if(!createRequested){
     createRequested=true;
     save();
     log("creating "+describe()+" from "+(spec.git()?"Git repository "+spec.repositoryUrl:
       "Object Storage bucket "+spec.bucketName)+(spec.variables.isEmpty()?"":" with variables "+spec.variables.keySet()));
    }else log("repeating the interrupted create request for "+describe());
    stack=api.createStack(spec.toDetails(defaultRegion()),retryToken);
    stackId=stack.getId();created=true;
    save();
    log("created stack "+stackId+" state "+state(stack.getLifecycleState()));
   }else{
    try{stack=api.getStack(stackId);}
    catch(Exception e){
     if(!retryStatus(e))throw e;
     log("transient error reading stack "+stackId+" (attempt "+transientStatusFailures+"/"+MAX_TRANSIENT_STATUS_FAILURES+
       "), retrying in "+pollingSeconds+"s: "+e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));
     return false;
    }
    transientStatusFailures=0;
   }
   return ready(api,stack.getLifecycleState());
  }
 }
 /** The one existing stack with the name, now this execution's stackId, or null when there is none. */
 private StackSummary findExisting(OciResourceManagerApi api) throws Exception {
  List<StackSummary> stacks=api.stacksNamed(spec.compartmentId,spec.stackName);
  if(stacks.isEmpty())return null;
  if(stacks.size()>1)throw new IllegalStateException(stacks.size()+" OCI Resource Manager stacks are named '"+spec.stackName+
    "' in compartment "+spec.compartmentId+" "+ids(stacks)+"; delete the extra stacks or rename them");
  StackSummary existing=stacks.get(0);
  if(failIfExists)throw new IllegalStateException("OCI Resource Manager "+describe()+" already exists: "+existing.getId()+
    " (ifExists: 'fail')");
  stackId=existing.getId();
  save();
  log("found existing stack "+stackId+" state "+state(existing.getLifecycleState())+"; configuration not compared or changed");
  return existing;
 }
 /** True once the stack is ACTIVE, false while it is CREATING; fails for any other state or after the timeout. */
 private boolean ready(OciResourceManagerApi api,Stack.LifecycleState state) throws Exception {
  if(state==Stack.LifecycleState.Active){
   if(created){
    // OCI allows several stacks with one name; a concurrent build may have created one at the same time. The list
    // may not show the new stack yet, so any other stack with the name counts, not the number of stacks.
    List<String> others=ids(api.stacksNamed(spec.compartmentId,spec.stackName)).stream().filter(id->!id.equals(stackId)).toList();
    if(!others.isEmpty()){
     List<String> all=new ArrayList<>(List.of(stackId));all.addAll(others);
     throw new IllegalStateException("Another stack named '"+spec.stackName+"' was created in compartment "+
       spec.compartmentId+" at the same time: "+all+"; delete the extra stacks and serialize builds that create stacks");
    }
   }
   log("stack "+stackId+" is ACTIVE");
   return true;
  }
  if(state==Stack.LifecycleState.Failed)throw new IOException("OCI Resource Manager stack "+stackId+" is FAILED; "+
    "check it in the OCI Console and delete it before running this stage again");
  if(state!=Stack.LifecycleState.Creating)
   throw new IOException("OCI Resource Manager stack "+stackId+" is "+state(state)+", not ACTIVE");
  if(System.currentTimeMillis()-startedAt()>TimeUnit.MINUTES.toMillis(timeoutMinutes))
   throw new IOException("Timed out waiting for OCI Resource Manager stack "+stackId+" to become ACTIVE");
  return false;
 }
 private String defaultRegion(){
  return spec.git()||spec.region!=null?null:OciRmConnections.get().require(connection).getRegion();
 }
 boolean retryStatus(Exception e){
  if(!OciRmBackend.isTransientBmc(e))return false;
  return ++transientStatusFailures<=MAX_TRANSIENT_STATUS_FAILURES;
 }
 private static List<String> ids(List<StackSummary> stacks){return stacks.stream().map(StackSummary::getId).toList();}
 private static String state(Stack.LifecycleState state){return state==null?"unknown":state.getValue();}
}
