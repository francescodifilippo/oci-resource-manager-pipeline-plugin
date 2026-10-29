package io.jenkins.plugins.iac.ocirm;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.iac.core.Identifiers;
import java.io.Serializable;
import java.util.List;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

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
  return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
 }
 @Extension public static final class DescriptorImpl extends Descriptor<OciRmConnection>{
  @Override public String getDisplayName(){return "OCI Resource Manager connection";}
  /** Global connections are admin-only; others only see the value already saved. */
  @POST public ListBoxModel doFillPrivateKeyCredentialsIdItems(@QueryParameter String privateKeyCredentialsId){
   return secretTextItems(privateKeyCredentialsId);
  }
  @POST public ListBoxModel doFillPassphraseCredentialsIdItems(@QueryParameter String passphraseCredentialsId){
   return secretTextItems(passphraseCredentialsId);
  }
  private static ListBoxModel secretTextItems(String current){
   StandardListBoxModel model=new StandardListBoxModel();
   if(!Jenkins.get().hasPermission(Jenkins.ADMINISTER))return model.includeCurrentValue(current);
   return model.includeEmptyValue()
     .includeMatchingAs(ACL.SYSTEM2,Jenkins.get(),StringCredentials.class,List.of(),CredentialsMatchers.always())
     .includeCurrentValue(current);
  }
 }
}
