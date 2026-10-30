package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.oracle.bmc.resourcemanager.ResourceManagerClient;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import com.sun.net.httpserver.HttpServer;
import hudson.util.Secret;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * The real OCI SDK client of a connection, with its endpoint moved to a local stub: Jersey, Jackson and the Apache
 * HTTP client come from the API plugins, not from the HPI, the SDK finds its HTTP client whatever the context class
 * loader, and the request is signed with the connection key.
 */
@WithJenkins
class OciRmClientTest {
 @Test void listsStacksWithASignedRequest(JenkinsRule j) throws Exception {
  KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");
  generator.initialize(2048);
  String pem="-----BEGIN PRIVATE KEY-----\n"+Base64.getMimeEncoder(64,"\n".getBytes(StandardCharsets.US_ASCII))
    .encodeToString(generator.generateKeyPair().getPrivate().getEncoded())+"\n-----END PRIVATE KEY-----\n";
  SystemCredentialsProvider.getInstance().getCredentials().add(
    new StringCredentialsImpl(CredentialsScope.GLOBAL,"oci-key","OCI key",Secret.fromString(pem)));
  OciRmConnections.get().setConnections(List.of(
    new OciRmConnection("oci","eu-frankfurt-1","ocid1.tenancy.oc1..t","ocid1.user.oc1..u","aa:bb","oci-key")));
  List<String> requests=new CopyOnWriteArrayList<>();
  HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  stub.createContext("/",exchange -> {
   requests.add(exchange.getRequestURI()+" "+exchange.getRequestHeaders().getFirst("Authorization"));
   byte[] body="[{\"id\":\"ocid1.ormstack.oc1..s\",\"displayName\":\"network\",\"lifecycleState\":\"ACTIVE\"}]"
     .getBytes(StandardCharsets.UTF_8);
   exchange.getResponseHeaders().set("Content-Type","application/json");
   exchange.sendResponseHeaders(200,body.length);
   try(OutputStream o=exchange.getResponseBody()){o.write(body);}
  });
  stub.start();
  WorkflowJob p=j.createProject(WorkflowJob.class);
  p.setDefinition(new CpsFlowDefinition("echo 'ok'",true));
  WorkflowRun b=j.buildAndAssertSuccess(p);
  // On a Jenkins thread the context class loader does not see the plugin's libraries; the platform loader stands for it.
  Thread thread=Thread.currentThread();
  ClassLoader previous=thread.getContextClassLoader();
  ResourceManagerClient built;
  thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
  try{built=OciRmConnections.get().client("oci",b);
   assertSame(ClassLoader.getPlatformClassLoader(),thread.getContextClassLoader());
  }finally{thread.setContextClassLoader(previous);}
  try(ResourceManagerClient client=built){
   client.setEndpoint("http://127.0.0.1:"+stub.getAddress().getPort());
   List<StackSummary> stacks=client.listStacks(ListStacksRequest.builder().compartmentId("ocid1.compartment.oc1..c")
     .displayName("network").build()).getItems();
   assertEquals(1,stacks.size());
   assertEquals("ocid1.ormstack.oc1..s",stacks.get(0).getId());
   assertEquals(Stack.LifecycleState.Active,stacks.get(0).getLifecycleState());
  }finally{stub.stop(0);}
  assertEquals(1,requests.size(),requests.toString());
  assertTrue(requests.get(0).startsWith("/20180917/stacks?"),requests.get(0));
  assertTrue(requests.get(0).contains("Signature ")&&requests.get(0).contains("keyId=\"ocid1.tenancy.oc1..t/ocid1.user.oc1..u/aa:bb\""),
    requests.get(0));
 }
}
