package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.model.Job;
import io.jenkins.plugins.iac.core.JobResult;

final class OciJobResultMapper {
 private OciJobResultMapper(){}
 static JobResult from(Job job){
  if(job==null)throw new IllegalArgumentException("OCI Resource Manager returned no job");
  return from(job.getId(),job.getLifecycleState());
 }
 static JobResult from(String id,Job.LifecycleState state){
  String value=state==null?"UNKNOWN":state.getValue();
  boolean successful="SUCCEEDED".equals(value);
  boolean completed=successful||"FAILED".equals(value)||"CANCELED".equals(value);
  return new JobResult(id,value,completed,successful,false);
 }
}
