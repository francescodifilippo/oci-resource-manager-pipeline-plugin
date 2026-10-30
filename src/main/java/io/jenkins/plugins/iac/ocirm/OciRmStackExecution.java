package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import hudson.ExtensionList;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.steps.*;

/**
 * Finds or creates the stack of an {@code ociRmStack} step without holding an executor, then runs the body once
 * the stack is ACTIVE. Safe to repeat after a restart: before the create request the execution persists its retry
 * token, and a resumed execution repeats that same request, which OCI answers with the stack already created.
 */
final class OciRmStackExecution extends StepExecution {
 private static final long serialVersionUID=1L;
 /** Consecutive transient stack-status failures tolerated before the step fails, as for jobs. */
 static final int MAX_TRANSIENT_STATUS_FAILURES=5;
 private final String connection;
 private final OciRmStackSpec spec;
 private final boolean failIfExists;
 private final int pollingSeconds,timeoutMinutes;
 private final String retryToken=UUID.randomUUID().toString();
 private long startedAt;
 /** Set and saved before the create request; from then on only that request (same retry token) is repeated. */
 private boolean createRequested;
 /** True when this execution created the stack, so that the name is checked again for a concurrent duplicate. */
 private boolean created;
 private String stackId;
 private int transientStatusFailures;
 private volatile boolean bodyStarted,stopped;
 private volatile BodyExecution body;
 private transient volatile Throwable stopCause;
 private transient ScheduledFuture<?> scheduled;

 OciRmStackExecution(StepContext context,String connection,OciRmStackSpec spec,boolean failIfExists,int pollingSeconds,int timeoutMinutes){
  super(context);this.connection=connection;this.spec=spec;this.failIfExists=failIfExists;
  this.pollingSeconds=pollingSeconds;this.timeoutMinutes=timeoutMinutes;
 }
 @Override public boolean start(){startedAt=System.currentTimeMillis();schedule(0);return false;}
 @Override public void onResume(){if(!bodyStarted)schedule(0);}
 private void schedule(long seconds){scheduled=Timer.get().schedule(this::advance,seconds,TimeUnit.SECONDS);}
 private void log(String m) throws IOException,InterruptedException {
  getContext().get(TaskListener.class).getLogger().println("IaC oci-rm: "+m);
 }
 private String describe(){return "stack '"+spec.stackName+"' in compartment "+spec.compartmentId;}

 private void advance(){
  if(bodyStarted||stopped)return;
  try{
   Run<?,?> run=getContext().get(Run.class);
   try(ResourceManager client=ExtensionList.lookupSingleton(OciRmBackend.class).clients.apply(connection,run)){
    OciResourceManagerApi api=new OciResourceManagerApi(client);
    if(stackId==null&&!createRequested&&findExisting(api))return;
    Stack stack;
    if(stackId==null){
     if(!createRequested){
      createRequested=true;
      getContext().saveState().get(1,TimeUnit.MINUTES);
      log("creating "+describe()+" from "+(spec.git()?"Git repository "+spec.repositoryUrl:
        "Object Storage bucket "+spec.bucketName)+(spec.variables.isEmpty()?"":" with variables "+spec.variables.keySet()));
     }else log("repeating the interrupted create request for "+describe());
     stack=api.createStack(spec.toDetails(defaultRegion()),retryToken);
     stackId=stack.getId();created=true;
     getContext().saveState();
     log("created stack "+stackId+" state "+state(stack.getLifecycleState()));
    }else{
     try{stack=api.getStack(stackId);}
     catch(Exception e){
      if(!retryStatus(e))throw e;
      log("transient error reading stack "+stackId+" (attempt "+transientStatusFailures+"/"+MAX_TRANSIENT_STATUS_FAILURES+
        "), retrying in "+pollingSeconds+"s: "+e.getClass().getSimpleName()+(e.getMessage()==null?"":": "+e.getMessage()));
      schedule(pollingSeconds);return;
     }
     transientStatusFailures=0;
    }
    handle(api,stack.getLifecycleState());
   }
  }catch(Throwable e){getContext().onFailure(e);}
 }
 /** True when an existing stack was taken over (and the next action already started or scheduled). */
 private boolean findExisting(OciResourceManagerApi api) throws Exception {
  List<StackSummary> stacks=api.stacksNamed(spec.compartmentId,spec.stackName);
  if(stacks.isEmpty())return false;
  if(stacks.size()>1)throw new IllegalStateException(stacks.size()+" OCI Resource Manager stacks are named '"+spec.stackName+
    "' in compartment "+spec.compartmentId+" "+ids(stacks)+"; delete the extra stacks or rename them");
  StackSummary existing=stacks.get(0);
  if(failIfExists)throw new IllegalStateException("OCI Resource Manager "+describe()+" already exists: "+existing.getId()+
    " (ifExists: 'fail')");
  stackId=existing.getId();
  getContext().saveState();
  log("found existing stack "+stackId+" state "+state(existing.getLifecycleState())+"; configuration not compared or changed");
  handle(api,existing.getLifecycleState());
  return true;
 }
 private void handle(OciResourceManagerApi api,Stack.LifecycleState state) throws Exception {
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
   startBody();return;
  }
  if(state==Stack.LifecycleState.Failed)throw new IOException("OCI Resource Manager stack "+stackId+" is FAILED; "+
    "check it in the OCI Console and delete it before running this stage again");
  if(state!=Stack.LifecycleState.Creating)
   throw new IOException("OCI Resource Manager stack "+stackId+" is "+state(state)+", not ACTIVE");
  if(System.currentTimeMillis()-startedAt>TimeUnit.MINUTES.toMillis(timeoutMinutes))
   throw new IOException("Timed out waiting for OCI Resource Manager stack "+stackId+" to become ACTIVE");
  schedule(pollingSeconds);
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
 private void startBody() throws Exception {
  if(stopped||bodyStarted)return;
  bodyStarted=true;
  getContext().saveState();
  BodyExecution started=getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).start();
  body=started;
  if(stopped)started.cancel(stopCause);
 }
 @Override public void stop(Throwable cause){
  stopCause=cause;
  stopped=true;
  if(scheduled!=null)scheduled.cancel(false);
  BodyExecution running=body;
  if(running!=null)running.cancel(cause);
  else getContext().onFailure(cause);
 }
}
