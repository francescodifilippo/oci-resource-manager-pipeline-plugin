package io.jenkins.plugins.iac.ocirm;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.Identifiers;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Declarative stage option: options { ociRmStack(...) }. Finds the stack named {@code stackName} in
 * {@code compartmentId}, or creates it from a Git or Object Storage configuration source, and runs the stage once
 * the stack is ACTIVE. It runs no job: later stages use {@code ociRmProvision(stackName:, compartmentId:)}.
 */
public final class OciRmStackStep extends Step {
 static final Set<String> IF_EXISTS=Set.of("keep","fail");
 private final String connection,stackName,compartmentId;
 private String description,terraformVersion,ifExists="keep";
 private String configurationSourceProviderId,repositoryUrl,branchName;
 private String bucketName,namespace,region;
 private String workingDirectory;
 private LinkedHashMap<String,String> variables=new LinkedHashMap<>();
 private int pollingSeconds=10,timeoutMinutes=30;

 @DataBoundConstructor public OciRmStackStep(String connection,String stackName,String compartmentId){
  this.connection=Identifiers.required(connection,"connection");
  this.stackName=text(stackName,"stackName",255);
  if(this.stackName==null)throw new IllegalArgumentException("stackName is required in the ociRmStack stage options");
  this.compartmentId=Identifiers.optionalOpaque(compartmentId,"compartmentId");
  if(this.compartmentId==null)throw new IllegalArgumentException("compartmentId is required in the ociRmStack stage options");
 }
 public String getConnection(){return connection;}
 public String getStackName(){return stackName;}
 public String getCompartmentId(){return compartmentId;}
 public String getDescription(){return description;}
 public String getTerraformVersion(){return terraformVersion;}
 public String getIfExists(){return ifExists;}
 public String getConfigurationSourceProviderId(){return configurationSourceProviderId;}
 public String getRepositoryUrl(){return repositoryUrl;}
 public String getBranchName(){return branchName;}
 public String getBucketName(){return bucketName;}
 public String getNamespace(){return namespace;}
 public String getRegion(){return region;}
 public String getWorkingDirectory(){return workingDirectory;}
 public Map<String,String> getVariables(){return variables.isEmpty()?null:Collections.unmodifiableMap(variables);}
 public int getPollingSeconds(){return pollingSeconds;}
 public int getTimeoutMinutes(){return timeoutMinutes;}

 @DataBoundSetter public void setDescription(String value){description=text(value,"description",4096);}
 @DataBoundSetter public void setTerraformVersion(String value){terraformVersion=text(value,"terraformVersion",64);}
 @DataBoundSetter public void setIfExists(String value){
  if(!IF_EXISTS.contains(value))throw new IllegalArgumentException("ifExists must be keep or fail");
  ifExists=value;
 }
 @DataBoundSetter public void setConfigurationSourceProviderId(String value){
  configurationSourceProviderId=Identifiers.optionalOpaque(value,"configurationSourceProviderId");
 }
 @DataBoundSetter public void setRepositoryUrl(String value){repositoryUrl=Identifiers.optionalOpaque(value,"repositoryUrl");}
 @DataBoundSetter public void setBranchName(String value){branchName=Identifiers.optionalOpaque(value,"branchName");}
 @DataBoundSetter public void setBucketName(String value){bucketName=Identifiers.optionalOpaque(value,"bucketName");}
 @DataBoundSetter public void setNamespace(String value){namespace=Identifiers.optionalOpaque(value,"namespace");}
 @DataBoundSetter public void setRegion(String value){region=Identifiers.optionalOpaque(value,"region");}
 @DataBoundSetter public void setWorkingDirectory(String value){workingDirectory=Identifiers.optionalOpaque(value,"workingDirectory");}
 /**
  * Plain-text Terraform variables of the stack. Pipeline passes a map literal's values as Groovy gives them
  * (numbers, booleans, GStrings), so each value is turned into its string form here.
  */
 @DataBoundSetter public void setVariables(Map<?,?> value){
  LinkedHashMap<String,String> checked=new LinkedHashMap<>();
  if(value!=null)for(Map.Entry<?,?> e:value.entrySet()){
   String key=e.getKey()==null?null:text(e.getKey().toString(),"variable name",255);
   if(key==null)throw new IllegalArgumentException("ociRmStack variable names must not be empty");
   Object v=e.getValue();
   if(!(v instanceof CharSequence||v instanceof Number||v instanceof Boolean))
    throw new IllegalArgumentException("ociRmStack variable "+key+" must be a string, number or boolean");
   checked.put(key,v.toString());
  }
  variables=checked;
 }
 @DataBoundSetter public void setPollingSeconds(int value){
  if(value<5||value>300)throw new IllegalArgumentException("pollingSeconds must be 5..300");
  pollingSeconds=value;
 }
 @DataBoundSetter public void setTimeoutMinutes(int value){
  if(value<1||value>1440)throw new IllegalArgumentException("timeoutMinutes must be 1..1440");
  timeoutMinutes=value;
 }

 /** Checks that exactly one configuration source is complete; runs when the stage starts, before any OCI call. */
 OciRmStackSpec spec(){
  boolean git=configurationSourceProviderId!=null||repositoryUrl!=null||branchName!=null;
  boolean objectStorage=bucketName!=null||namespace!=null||region!=null;
  if(git&&objectStorage)throw new IllegalArgumentException("Use either a Git source (configurationSourceProviderId, repositoryUrl, "+
    "branchName) or an Object Storage source (bucketName, namespace, region) in the ociRmStack stage options, not both");
  if(!git&&!objectStorage)throw new IllegalArgumentException("ociRmStack needs a configuration source in the stage options: "+
    "configurationSourceProviderId with repositoryUrl (Git), or bucketName with namespace (Object Storage)");
  if(git&&(configurationSourceProviderId==null||repositoryUrl==null))
   throw new IllegalArgumentException("A Git source needs both configurationSourceProviderId and repositoryUrl in the ociRmStack stage options");
  if(objectStorage&&(bucketName==null||namespace==null))
   throw new IllegalArgumentException("An Object Storage source needs both bucketName and namespace in the ociRmStack stage options");
  return new OciRmStackSpec(compartmentId,stackName,description,terraformVersion,configurationSourceProviderId,repositoryUrl,
    branchName,bucketName,namespace,region,workingDirectory,variables);
 }
 @Override public StepExecution start(StepContext context){
  return new OciRmStackExecution(context,connection,spec(),"fail".equals(ifExists),pollingSeconds,timeoutMinutes);
 }
 private static String text(String value,String name,int max){
  if(value==null||value.isBlank())return null;
  if(value.length()>max||value.chars().anyMatch(c->c<32||c==127))
   throw new IllegalArgumentException(name+" must be 1.."+max+" characters without control characters");
  return value;
 }
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "ociRmStack";}
  @Override public String getDisplayName(){return "Find or create an OCI Resource Manager stack (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}
