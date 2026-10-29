package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.ExtensionList;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.util.List;
import jenkins.model.Jenkins;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OciRmConnectionsTest {
 private static final String TENANCY="ocid1.tenancy.oc1..testtenancy";
 private JenkinsRule j;

 @BeforeEach void setUp(JenkinsRule rule) throws Exception {
  j=rule;
  var store=SystemCredentialsProvider.getInstance().getCredentials();
  store.add(new StringCredentialsImpl(CredentialsScope.GLOBAL,"oci-key","OCI key",Secret.fromString("not-a-real-key")));
  store.add(new StringCredentialsImpl(CredentialsScope.GLOBAL,"oci-pass","OCI passphrase",Secret.fromString("not-a-real-passphrase")));
  SystemCredentialsProvider.getInstance().save();
 }

 private static OciRmConnection connection(){
  OciRmConnection c=new OciRmConnection("oci-prod","eu-frankfurt-1",TENANCY,"ocid1.user.oc1..testuser","aa:bb:cc","oci-key");
  c.setPassphraseCredentialsId("oci-pass");
  return c;
 }

 @Test void roundtripsOneConnectionWithCredentialSelects() throws Exception {
  OciRmConnections.get().setConnections(List.of(connection()));
  HtmlPage page=j.createWebClient().goTo("configure");
  assertNotNull(page.querySelector("select.credentials-select[name='_.privateKeyCredentialsId']"),"private key select rendered");
  assertNotNull(page.querySelector("select.credentials-select[name='_.passphraseCredentialsId']"),"passphrase select rendered");
  // The selects are filled by AJAX; an empty private key submission would be rejected by OciRmConnection.
  j.configRoundtrip();
  List<OciRmConnection> connections=OciRmConnections.get().getConnections();
  assertEquals(1,connections.size());
  OciRmConnection c=connections.get(0);
  assertEquals("oci-prod",c.getName());
  assertEquals("eu-frankfurt-1",c.getRegion());
  assertEquals(TENANCY,c.getTenancyId());
  assertEquals("ocid1.user.oc1..testuser",c.getUserId());
  assertEquals("aa:bb:cc",c.getFingerprint());
  assertEquals("oci-key",c.getPrivateKeyCredentialsId());
  assertEquals("oci-pass",c.getPassphraseCredentialsId());
 }

 @Test void deletingTheLastConnectionClearsTheList() throws Exception {
  OciRmConnections.get().setConnections(List.of(connection()));
  HtmlPage page=j.createWebClient().goTo("configure");
  HtmlForm form=page.getFormByName("config");
  DomNode chunk=form.querySelector("input[name='_.tenancyId'][value='"+TENANCY+"']");
  while(chunk!=null && !(chunk instanceof DomElement e && e.getAttribute("class").contains("repeated-chunk")))chunk=chunk.getParentNode();
  assertNotNull(chunk,"connection entry rendered");
  chunk.remove();
  j.submit(form);
  assertEquals(List.of(),OciRmConnections.get().getConnections());
  j.configRoundtrip();
  assertEquals(List.of(),OciRmConnections.get().getConnections());
 }

 @Test void credentialItemsRequireAdminister() {
  j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
  j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
    .grant(Jenkins.ADMINISTER).everywhere().to("admin").grant(Jenkins.READ).everywhere().to("reader"));
  OciRmConnection.DescriptorImpl d=ExtensionList.lookupSingleton(OciRmConnection.DescriptorImpl.class);
  try(ACLContext ctx=ACL.as(User.getById("admin",true))){
   assertTrue(values(d.doFillPrivateKeyCredentialsIdItems("")).contains("oci-key"),"admin sees credentials");
   assertTrue(values(d.doFillPassphraseCredentialsIdItems("")).contains("oci-pass"),"admin sees credentials");
  }
  try(ACLContext ctx=ACL.as(User.getById("reader",true))){
   assertEquals(List.of("saved-key"),values(d.doFillPrivateKeyCredentialsIdItems("saved-key")),"non-admin only sees the current value");
   assertEquals(List.of("saved-pass"),values(d.doFillPassphraseCredentialsIdItems("saved-pass")),"non-admin only sees the current value");
  }
 }

 private static List<String> values(ListBoxModel items){return items.stream().map(o->o.value).toList();}
}
