package io.jenkins.plugins.iac.ocirm;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.AbstractProvisionStep;
import io.jenkins.plugins.iac.core.Identifiers;
import java.util.*;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Declarative stage option: options { ociRmProvision(...) }. The stack is either {@code stackId} or
 * {@code stackName} with {@code compartmentId}, which {@link OciRmBackend#resolve} looks up when the stage runs.
 */
public final class OciRmProvisionStep extends AbstractProvisionStep {
 private String mode="plan",displayName,planOperationKey,planJobId,stackName,compartmentId;
 private boolean autoApprove;

 /** stackId is an optional setter so that Pipeline Syntax does not list it as required next to stackName. */
 @DataBoundConstructor public OciRmProvisionStep(String connection){super(connection);}
 /** Convenience for Java callers; Pipeline binds stackId through {@link #setStackId}. */
 public OciRmProvisionStep(String connection,String stackId){super(connection,stackId);}
 public String getConnection(){return getConnectionId();}
 public String getStackId(){return getTargetId();}
 public String getStackName(){return stackName;}
 public String getCompartmentId(){return compartmentId;}
 public String getMode(){return mode;}
 public String getDisplayName(){return displayName;}
 public String getPlanOperationKey(){return planOperationKey;}
 public String getPlanJobId(){return planJobId;}
 public boolean isAutoApprove(){return autoApprove;}

 @DataBoundSetter public void setMode(String value){
  if(!Set.of("plan","apply","destroy").contains(value))throw new IllegalArgumentException("mode must be plan/apply/destroy");
  mode=value;
 }
 @DataBoundSetter public void setStackId(String value){setTargetId(value);}
 @DataBoundSetter public void setStackName(String value){stackName=optionalText(value,"stackName",255);}
 @DataBoundSetter public void setCompartmentId(String value){compartmentId=Identifiers.optionalOpaque(value,"compartmentId");}
 @DataBoundSetter public void setDisplayName(String value){displayName=optionalText(value,"displayName",255);}
 @DataBoundSetter public void setPlanOperationKey(String value){planOperationKey=Identifiers.optional(value,"planOperationKey");}
 @DataBoundSetter public void setPlanJobId(String value){planJobId=Identifiers.optionalOpaque(value,"planJobId");}
 @DataBoundSetter public void setAutoApprove(boolean value){autoApprove=value;}

 @Override protected String provider(){return "oci-rm";}
 @Override protected Map<String,String> parameters(){
  String stackId=getTargetId();
  if(stackId!=null&&stackName!=null)
   throw new IllegalArgumentException("Use either stackId or stackName in the ociRmProvision stage options, not both");
  if(stackId==null&&stackName==null)
   throw new IllegalArgumentException("ociRmProvision needs stackId, or stackName with compartmentId, in the stage options");
  if(stackName!=null&&compartmentId==null)
   throw new IllegalArgumentException("stackName needs compartmentId (OCID of the compartment that contains the stack) in the ociRmProvision stage options");
  if(stackId!=null&&compartmentId!=null)
   throw new IllegalArgumentException("compartmentId is used only with stackName; remove it from the ociRmProvision stage options when stackId is set");
  if(!"apply".equals(mode)&&(planOperationKey!=null||planJobId!=null))
   throw new IllegalArgumentException("planOperationKey/planJobId are valid only for mode=apply");
  if("apply".equals(mode)&&planOperationKey!=null&&planJobId!=null)
   throw new IllegalArgumentException("Use either planOperationKey or planJobId, not both");
  if("apply".equals(mode)&&planOperationKey==null&&planJobId==null&&!autoApprove)
   throw new IllegalArgumentException("OCI apply requires planOperationKey/planJobId or autoApprove=true");
  if("destroy".equals(mode)&&!autoApprove)throw new IllegalArgumentException(OciResourceManagerApi.DESTROY_NEEDS_APPROVAL);
  Map<String,String> p=new LinkedHashMap<>();p.put("mode",mode);
  if(stackName!=null){p.put("stackName",stackName);p.put("compartmentId",compartmentId);}
  if(displayName!=null)p.put("displayName",displayName);
  if(planOperationKey!=null)p.put("planOperationKey",planOperationKey);
  if(planJobId!=null)p.put("planJobId",planJobId);
  if(autoApprove)p.put("autoApprove","true");
  return p;
 }
 private static String optionalText(String value,String name,int max){
  if(value==null||value.isBlank())return null;
  if(value.length()>max||value.chars().anyMatch(c->c<32||c==127))
   throw new IllegalArgumentException(name+" must be 1.."+max+" characters without control characters");
  return value;
 }
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "ociRmProvision";}
  @Override public String getDisplayName(){return "OCI Resource Manager job (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}
