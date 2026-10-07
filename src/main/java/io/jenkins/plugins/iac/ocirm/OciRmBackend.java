package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManagerClient;
import hudson.Extension;
import hudson.model.Run;
import io.jenkins.plugins.iac.core.*;
import java.util.*;

@Extension public final class OciRmBackend implements IacBackend {
 @Override public String id(){return "oci-rm";}
 @Override public boolean supportsIdempotentSubmit(){return true;}

 @Override public Map<String,String> operationMetadata(SubmissionRequest request){
  Map<String,String> metadata=new LinkedHashMap<>();
  copy(request.parameters(),metadata,"mode");
  copy(request.parameters(),metadata,"planOperationKey");
  copy(request.parameters(),metadata,"planJobId");
  return metadata;
 }
 @Override public JobResult submit(SubmissionRequest request,Run<?,?> run) throws Exception {
  Map<String,String> p=request.parameters();
  String mode=p.getOrDefault("mode","plan");
  String planJobId=resolvePlanJobId(mode,p,request,run);
  boolean autoApprove=Boolean.parseBoolean(p.getOrDefault("autoApprove","false"));
  try(ResourceManagerClient client=OciRmConnections.get().client(request.connectionId(),run)){
   return new OciResourceManagerApi(client).submit(request.targetId(),mode,p.get("displayName"),planJobId,autoApprove,request.requestToken());
  }
 }
 @Override public JobResult status(RemoteOperation operation,Run<?,?> run) throws Exception {
  try(ResourceManagerClient client=OciRmConnections.get().client(operation.connectionId(),run)){
   return new OciResourceManagerApi(client).status(operation.remoteId());
  }
 }
 private String resolvePlanJobId(String mode,Map<String,String> p,SubmissionRequest request,Run<?,?> run){
  String direct=blankToNull(p.get("planJobId"));
  String key=blankToNull(p.get("planOperationKey"));
  if(!"apply".equals(mode)){
   if(direct!=null||key!=null)throw new IllegalArgumentException("planJobId/planOperationKey are valid only for mode=apply");
   return null;
  }
  if(direct!=null&&key!=null)throw new IllegalArgumentException("Use either planJobId or planOperationKey, not both");
  if(direct!=null)return direct;
  if(key==null)return null;
  RemoteOperation plan=OperationStoreAction.forBuild(run).get(key);
  if(plan==null)throw new IllegalArgumentException("No OCI plan operation found in this build: "+key);
  if(!id().equals(plan.provider()))throw new IllegalArgumentException("Operation "+key+" was created by provider "+plan.provider());
  if(!request.connectionId().equals(plan.connectionId()))throw new IllegalArgumentException("Plan "+key+" uses a different OCI connection");
  if(!request.targetId().equals(plan.targetId()))throw new IllegalArgumentException("Plan "+key+" belongs to a different OCI stack");
  if(plan.remoteId()==null)throw new IllegalStateException("Plan "+key+" has no saved OCI job OCID");
  if(!"SUCCEEDED".equals(plan.status()))throw new IllegalStateException("Plan "+key+" is not successful: "+plan.status());
  return plan.remoteId();
 }
 private static void copy(Map<String,String> from,Map<String,String> to,String key){
  String value=blankToNull(from.get(key));if(value!=null)to.put(key,value);
 }
 private static String blankToNull(String value){return value==null||value.isBlank()?null:value;}
}
