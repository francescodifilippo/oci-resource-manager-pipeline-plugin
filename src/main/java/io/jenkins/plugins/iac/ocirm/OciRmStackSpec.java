package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.model.CreateConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateGitConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateObjectStorageConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateStackDetails;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validated, non-secret description of the stack an {@code ociRmStack} step finds or creates, persisted with the
 * step execution. The configuration source is either Git ({@code configurationSourceProviderId} set) or Object
 * Storage ({@code bucketName} set).
 */
final class OciRmStackSpec implements Serializable {
 private static final long serialVersionUID=1L;
 final String compartmentId,stackName,description,terraformVersion;
 final String configurationSourceProviderId,repositoryUrl,branchName;
 final String bucketName,namespace,region;
 final String workingDirectory;
 final LinkedHashMap<String,String> variables;

 OciRmStackSpec(String compartmentId,String stackName,String description,String terraformVersion,
   String configurationSourceProviderId,String repositoryUrl,String branchName,
   String bucketName,String namespace,String region,String workingDirectory,Map<String,String> variables){
  this.compartmentId=compartmentId;this.stackName=stackName;this.description=description;this.terraformVersion=terraformVersion;
  this.configurationSourceProviderId=configurationSourceProviderId;this.repositoryUrl=repositoryUrl;this.branchName=branchName;
  this.bucketName=bucketName;this.namespace=namespace;this.region=region;this.workingDirectory=workingDirectory;
  this.variables=variables==null?new LinkedHashMap<>():new LinkedHashMap<>(variables);
 }
 boolean git(){return configurationSourceProviderId!=null;}

 /** @param defaultRegion region of the connection, used for an Object Storage source without {@code region} */
 CreateStackDetails toDetails(String defaultRegion){
  // The SDK sends every explicitly set field, null included, so optional fields are set only when given.
  CreateConfigSourceDetails source;
  if(git()){
   CreateGitConfigSourceDetails.Builder b=CreateGitConfigSourceDetails.builder()
     .configurationSourceProviderId(configurationSourceProviderId).repositoryUrl(repositoryUrl);
   if(branchName!=null)b.branchName(branchName);
   if(workingDirectory!=null)b.workingDirectory(workingDirectory);
   source=b.build();
  }else{
   CreateObjectStorageConfigSourceDetails.Builder b=CreateObjectStorageConfigSourceDetails.builder()
     .namespace(namespace).bucketName(bucketName).region(region==null?defaultRegion:region);
   if(workingDirectory!=null)b.workingDirectory(workingDirectory);
   source=b.build();
  }
  CreateStackDetails.Builder details=CreateStackDetails.builder().compartmentId(compartmentId).displayName(stackName)
    .configSource(source);
  if(description!=null)details.description(description);
  if(terraformVersion!=null)details.terraformVersion(terraformVersion);
  if(!variables.isEmpty())details.variables(new LinkedHashMap<>(variables));
  return details.build();
 }
}
