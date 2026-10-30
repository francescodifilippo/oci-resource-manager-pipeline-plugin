package io.jenkins.plugins.iac.ocirm;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.resourcemanager.ResourceManagerClient;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Descriptor.FormException;
import hudson.model.Run;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jenkins.model.GlobalConfiguration;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest2;

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
 /** A repeatable list with every entry deleted submits no "connections" key at all, so binding alone would keep the old list. */
 @Override public boolean configure(StaplerRequest2 req,JSONObject json) throws FormException {
  boolean noConnections=!json.has("connections");
  req.bindJSON(this,json);
  if(noConnections)setConnections(List.of());
  return true;
 }
 public OciRmConnection require(String name){
  return getConnections().stream().filter(c->c.getName().equals(name)).findFirst()
   .orElseThrow(()->new IllegalArgumentException("Unknown OCI Resource Manager connection: "+name));
 }
 public ResourceManagerClient client(String name,Run<?,?> run){
  OciRmConnection c=require(name);
  byte[] privateKey=privateKey(c.getPrivateKeyCredentialsId(),run);
  SimpleAuthenticationDetailsProvider.SimpleAuthenticationDetailsProviderBuilder auth=
   SimpleAuthenticationDetailsProvider.builder()
    .tenantId(c.getTenancyId()).userId(c.getUserId()).fingerprint(c.getFingerprint())
    .privateKeySupplier(()->new ByteArrayInputStream(privateKey));
  if(c.getPassphraseCredentialsId()!=null)
   auth.passphraseCharacters(secret(c.getPassphraseCredentialsId(),run,"private-key passphrase").toCharArray());
  // The SDK finds its HTTP client (Jersey) with ServiceLoader and the context class loader, which on a Jenkins
  // thread does not see this plugin's libraries.
  Thread thread=Thread.currentThread();
  ClassLoader previous=thread.getContextClassLoader();
  thread.setContextClassLoader(OciRmConnections.class.getClassLoader());
  try{
   ResourceManagerClient client=ResourceManagerClient.builder().build(auth.build());
   client.setRegion(c.getRegion());
   return client;
  }finally{thread.setContextClassLoader(previous);}
 }
 /** PEM private key from a Secret File credential or, for compatibility, a Secret Text credential. Never logged. */
 static byte[] privateKey(String id,Run<?,?> run){
  FileCredentials file=CredentialsProvider.findCredentialById(id,FileCredentials.class,run,Collections.emptyList());
  if(file!=null){
   try(InputStream in=file.getContent()){return in.readAllBytes();}
   catch(IOException e){throw new IllegalStateException("Cannot read OCI private key Secret File credential: "+id,e);}
  }
  StringCredentials text=CredentialsProvider.findCredentialById(id,StringCredentials.class,run,Collections.emptyList());
  if(text==null)throw new IllegalArgumentException("Missing or inaccessible OCI private key credential (Secret File or Secret Text): "+id);
  return normalizePem(text.getSecret().getPlainText()).getBytes(StandardCharsets.UTF_8);
 }
 private static final Pattern FLAT_PEM=Pattern.compile("\\s*(-----BEGIN ([A-Z0-9 ]+)-----)([A-Za-z0-9+/=\\s]+)(-----END \\2-----)\\s*");
 /**
  * A PEM pasted into a single-line Secret Text field loses its line breaks. Restore them only for a plain
  * base64 body (no encryption headers); anything else is returned unchanged.
  */
 static String normalizePem(String key){
  if(key.indexOf('\n')>=0)return key;
  Matcher m=FLAT_PEM.matcher(key);
  if(!m.matches())return key;
  String body=m.group(3).replaceAll("\\s","");
  StringBuilder b=new StringBuilder(m.group(1)).append('\n');
  for(int i=0;i<body.length();i+=64)b.append(body,i,Math.min(body.length(),i+64)).append('\n');
  return b.append(m.group(4)).append('\n').toString();
 }
 private static String secret(String id,Run<?,?> run,String label){
  StringCredentials credential=CredentialsProvider.findCredentialById(id,StringCredentials.class,run,Collections.emptyList());
  if(credential==null)throw new IllegalArgumentException("Missing or inaccessible OCI "+label+" credential: "+id);
  return credential.getSecret().getPlainText();
 }
 public static OciRmConnections get(){return ExtensionList.lookupSingleton(OciRmConnections.class);}
}
