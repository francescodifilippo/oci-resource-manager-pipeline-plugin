package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.oracle.bmc.resourcemanager.ResourceManagerClient;
import com.oracle.bmc.resourcemanager.model.CreateGitConfigSourceDetails;
import com.oracle.bmc.resourcemanager.model.CreateStackDetails;
import com.oracle.bmc.resourcemanager.model.GitConfigSource;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.requests.CreateStackRequest;
import com.oracle.bmc.resourcemanager.requests.GetStackRequest;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import com.sun.net.httpserver.HttpServer;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.util.Secret;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import jenkins.util.Timer;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * The OCI SDK inside a real Jenkins, where every plugin has its own class loader: on a timer thread, as setup steps
 * run, the client of a connection lists, creates and reads a stack from a local stub, and parses its answers.
 */
class OciRmRealJenkinsTest {
 @RegisterExtension
 private final RealJenkinsExtension rr=new RealJenkinsExtension();

 @Test void theSdkWorksInPluginClassLoaders() throws Throwable {
  rr.then(OciRmRealJenkinsTest::listCreateAndGet);
 }

 private static void listCreateAndGet(JenkinsRule j) throws Throwable {
  HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  stub.createContext("/20180917/stacks",exchange -> {
   String path=exchange.getRequestURI().getPath(),stack="{\"id\":\"ocid1.ormstack.oc1..new\",\"displayName\":\"network\","+
     "\"lifecycleState\":\"%s\",\"configSource\":{\"configSourceType\":\"GIT_CONFIG_SOURCE\","+
     "\"configurationSourceProviderId\":\"ocid1.ormconfigsourceprovider.oc1..p\",\"repositoryUrl\":\"https://example.com/n.git\"}}";
   String body="POST".equals(exchange.getRequestMethod())?stack.formatted("CREATING"):
     path.endsWith("/ocid1.ormstack.oc1..new")?stack.formatted("ACTIVE"):"[]";
   byte[] b=body.getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(200,b.length);
   try(OutputStream o=exchange.getResponseBody()){o.write(b);}
  });
  stub.start();
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
  generator.initialize(2048);
  String pem="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.US_ASCII))
    .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
  try(ACLContext ignored=ACL.as2(ACL.SYSTEM2)){
   SystemCredentialsProvider.getInstance().getCredentials().add(
     new StringCredentialsImpl(CredentialsScope.GLOBAL,"oci-key","OCI key",Secret.fromString(pem)));
   OciRmConnections.get().setConnections(List.of(
     new OciRmConnection("oci","eu-frankfurt-1","ocid1.tenancy.oc1..t","ocid1.user.oc1..u","aa:bb","oci-key")));
  }
  WorkflowJob p=j.createProject(WorkflowJob.class);
  p.setDefinition(new CpsFlowDefinition("echo 'ok'",true));
  WorkflowRun b=j.buildAndAssertSuccess(p);
  String endpoint="http://127.0.0.1:"+stub.getAddress().getPort();
  try{
   String result=Timer.get().submit(() -> {
    try(ResourceManagerClient client=OciRmConnections.get().client("oci",b)){
     client.setEndpoint(endpoint);
     int listed=client.listStacks(ListStacksRequest.builder().compartmentId("ocid1.compartment.oc1..c").displayName("network")
       .build()).getItems().size();
     Stack created=client.createStack(CreateStackRequest.builder().opcRetryToken("token-1").createStackDetails(
       CreateStackDetails.builder().compartmentId("ocid1.compartment.oc1..c").displayName("network").configSource(
         CreateGitConfigSourceDetails.builder().configurationSourceProviderId("ocid1.ormconfigsourceprovider.oc1..p")
           .repositoryUrl("https://example.com/n.git").build()).build()).build()).getStack();
     Stack read=client.getStack(GetStackRequest.builder().stackId(created.getId()).build()).getStack();
     return listed+" "+created.getLifecycleState()+" "+read.getLifecycleState()+" "+
       ((GitConfigSource)read.getConfigSource()).getRepositoryUrl();
    }
   }).get();
   assertEquals("0 Creating Active https://example.com/n.git",result);
  }finally{stub.stop(0);}
 }
}
