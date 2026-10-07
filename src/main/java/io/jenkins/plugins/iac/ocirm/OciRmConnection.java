package io.jenkins.plugins.iac.ocirm;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import io.jenkins.plugins.iac.core.Identifiers;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public final class OciRmConnection implements Describable<OciRmConnection>, Serializable {
 private static final long serialVersionUID=1L;
 private final String name,region,tenancyId,userId,fingerprint,privateKeyCredentialsId;
 private String passphraseCredentialsId;

 @DataBoundConstructor public OciRmConnection(String name,String region,String tenancyId,String userId,
   String fingerprint,String privateKeyCredentialsId){
  this.name=Identifiers.required(name,"connection");
  this.region=Identifiers.opaque(region,"region");
  this.tenancyId=Identifiers.opaque(tenancyId,"tenancyId");
  this.userId=Identifiers.opaque(userId,"userId");
  this.fingerprint=Identifiers.opaque(fingerprint,"fingerprint");
  this.privateKeyCredentialsId=Identifiers.required(privateKeyCredentialsId,"privateKeyCredentialsId");
 }
 public String getName(){return name;}
 public String getRegion(){return region;}
 public String getTenancyId(){return tenancyId;}
 public String getUserId(){return userId;}
 public String getFingerprint(){return fingerprint;}
 public String getPrivateKeyCredentialsId(){return privateKeyCredentialsId;}
 public String getPassphraseCredentialsId(){return passphraseCredentialsId;}
 @DataBoundSetter public void setPassphraseCredentialsId(String value){
  passphraseCredentialsId=Identifiers.optional(value,"passphraseCredentialsId");
 }
 @Override public Descriptor<OciRmConnection> getDescriptor(){
  return jenkins.model.Jenkins.get().getDescriptorByType(DescriptorImpl.class);
 }
 @Extension public static final class DescriptorImpl extends Descriptor<OciRmConnection>{
  @Override public String getDisplayName(){return "OCI Resource Manager connection";}
 }
}
