package io.jenkins.plugins.iac.ocirm;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.resourcemanager.ResourceManagerClient;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Run;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;

@Extension public final class OciRmConnections extends GlobalConfiguration {
 private List<OciRmConnection> connections=new ArrayList<>();
 public OciRmConnections(){load();}
 @Override public String getDisplayName(){return "OCI Resource Manager connections";}
 public List<OciRmConnection> getConnections(){return connections==null?List.of():List.copyOf(connections);}
 @DataBoundSetter public void setConnections(List<OciRmConnection> input){
  jenkins.model.Jenkins.get().checkPermission(jenkins.model.Jenkins.ADMINISTER);
  List<OciRmConnection> checked=input==null?List.of():List.copyOf(input);
  Set<String> names=new HashSet<>();
  for(OciRmConnection c:checked) if(!names.add(c.getName()))
   throw new IllegalArgumentException("Duplicate OCI Resource Manager connection: "+c.getName());
  connections=new ArrayList<>(checked);save();
 }
 public OciRmConnection require(String name){
  return getConnections().stream().filter(c->c.getName().equals(name)).findFirst()
   .orElseThrow(()->new IllegalArgumentException("Unknown OCI Resource Manager connection: "+name));
 }
 public ResourceManagerClient client(String name,Run<?,?> run){
  OciRmConnection c=require(name);
  String privateKey=secret(c.getPrivateKeyCredentialsId(),run,"private key");
  SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder auth=
   SimpleAuthenticationDetailsProvider.builder()
    .tenantId(c.getTenancyId()).userId(c.getUserId()).fingerprint(c.getFingerprint())
    .privateKeySupplier(()->new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)));
  if(c.getPassphraseCredentialsId()!=null)
   auth.passphraseCharacters(secret(c.getPassphraseCredentialsId(),run,"private-key passphrase").toCharArray());
  ResourceManagerClient client=ResourceManagerClient.builder().build(auth.build());
  client.setRegion(c.getRegion());
  return client;
 }
 private static String secret(String id,Run<?,?> run,String label){
  StringCredentials credential=CredentialsProvider.findCredentialById(id,StringCredentials.class,run,Collections.emptyList());
  if(credential==null)throw new IllegalArgumentException("Missing or inaccessible OCI "+label+" credential: "+id);
  return credential.getSecret().getPlainText();
 }
 public static OciRmConnections get(){return ExtensionList.lookupSingleton(OciRmConnections.class);}
}
