package io.jenkins.plugins.iac.ocirm;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.iac.core.AbstractAwaitStep;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/** Declarative stage option: options { ociRmAwait(...) } */
public final class OciRmAwaitStep extends AbstractAwaitStep {
 @DataBoundConstructor public OciRmAwaitStep(String connection,String operationKey){super(connection,operationKey);}
 public String getConnection(){return getConnectionId();}
 @Override protected String provider(){return "oci-rm";}
 @Extension public static final class DescriptorImpl extends StepDescriptor {
  @Override public String getFunctionName(){return "ociRmAwait";}
  @Override public String getDisplayName(){return "Await previous OCI Resource Manager job (Declarative stage option)";}
  @Override public boolean takesImplicitBlockArgument(){return true;}
  @Override public Set<? extends Class<?>> getRequiredContext(){return Set.of(Run.class,TaskListener.class);}
 }
}
