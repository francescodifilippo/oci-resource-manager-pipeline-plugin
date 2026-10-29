package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** How Pipeline (and the Snippet Generator) sees the step's parameters. */
@WithJenkins
class OciRmProvisionStepModelTest {
 private static final String STACK="ocid1.ormstack.oc1..example",COMPARTMENT="ocid1.compartment.oc1..example";
 private final DescribableModel<OciRmProvisionStep> model=DescribableModel.of(OciRmProvisionStep.class);

 @Test void onlyConnectionIsRequired(){
  assertTrue(model.getParameter("connection").isRequired());
  for(String name:new String[]{"stackId","stackName","compartmentId"})assertFalse(model.getParameter(name).isRequired(),name);
 }
 @Test void stackIdRoundTrips(JenkinsRule j) throws Exception {
  OciRmProvisionStep step=model.instantiate(Map.of("connection","oci","stackId",STACK,"mode","plan"));
  assertEquals(STACK,step.getStackId());
  assertNull(step.getStackName());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("oci",args.get("connection"));
  assertEquals(STACK,args.get("stackId"));
  assertFalse(args.containsKey("stackName"));
  assertFalse(args.containsKey("compartmentId"));
 }
 @Test void stackNameRoundTrips(JenkinsRule j) throws Exception {
  OciRmProvisionStep step=model.instantiate(Map.of("connection","oci","stackName","network","compartmentId",COMPARTMENT));
  assertNull(step.getStackId());
  Map<String,?> args=DescribableModel.uninstantiate2_(step).getArguments();
  assertEquals("network",args.get("stackName"));
  assertEquals(COMPARTMENT,args.get("compartmentId"));
  assertFalse(args.containsKey("stackId"));
 }
 @Test void snippetsShowOnlyTheConfiguredStackParameters(JenkinsRule j){
  OciRmProvisionStep byId=new OciRmProvisionStep("oci");byId.setStackId(STACK);
  String snippet=Snippetizer.object2Groovy(byId);
  assertTrue(snippet.startsWith("ociRmProvision(connection: 'oci', stackId: '"+STACK+"')"),snippet);
  OciRmProvisionStep byName=new OciRmProvisionStep("oci");byName.setStackName("network");byName.setCompartmentId(COMPARTMENT);
  snippet=Snippetizer.object2Groovy(byName);
  assertTrue(snippet.contains("stackName: 'network'")&&snippet.contains("compartmentId: '"+COMPARTMENT+"'"),snippet);
  assertFalse(snippet.contains("stackId"),snippet);
 }
 @Test void javaConvenienceConstructorSetsStackId(){
  assertEquals(STACK,new OciRmProvisionStep("oci",STACK).getStackId());
 }
}
