package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.CreateGitConfigSourceDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** How Pipeline (and the Snippet Generator) sees the ociRmStack parameters. */
@WithJenkins
class OciRmStackStepModelTest {
 private static final String COMPARTMENT="ocid1.compartment.oc1..example";
 private static final String PROVIDER="ocid1.ormconfigsourceprovider.oc1..github";
 private final DescribableModel<OciRmStackStep> model=DescribableModel.of(OciRmStackStep.class);

 @Test void connectionStackNameAndCompartmentAreRequired(){
  for(String name:new String[]{"connection","stackName","compartmentId"})assertTrue(model.getParameter(name).isRequired(),name);
  for(String name:new String[]{"configurationSourceProviderId","bucketName","variables","ifExists"})
   assertFalse(model.getParameter(name).isRequired(),name);
 }
 @Test void snippetShowsTheConfiguredParameters(JenkinsRule j){
  OciRmStackStep step=new OciRmStackStep("oci","network",COMPARTMENT);
  step.setConfigurationSourceProviderId(PROVIDER);
  step.setRepositoryUrl("https://github.com/example/network.git");
  Map<String,Object> variables=new LinkedHashMap<>();variables.put("region","eu-frankfurt-1");variables.put("count",3);
  step.setVariables(variables);
  String snippet=Snippetizer.object2Groovy(step);
  assertTrue(snippet.startsWith("ociRmStack(compartmentId: '"+COMPARTMENT+"', configurationSourceProviderId: '"+PROVIDER+"', connection: 'oci'"),snippet);
  assertTrue(snippet.contains("variables: [region: 'eu-frankfurt-1', count: '3']"),snippet);
  assertFalse(snippet.contains("bucketName"),snippet);
 }
 @Test void blankNamesAreRejected(){
  var e=assertThrows(IllegalArgumentException.class,()->new OciRmStackStep("oci"," ",COMPARTMENT));
  assertEquals("stackName is required in the ociRmStack stage options",e.getMessage());
  e=assertThrows(IllegalArgumentException.class,()->new OciRmStackStep("oci","network",""));
  assertEquals("compartmentId is required in the ociRmStack stage options",e.getMessage());
 }
 @Test void gitDetailsLeaveUnsetFieldsOut(){
  OciRmStackStep step=new OciRmStackStep("oci","network",COMPARTMENT);
  step.setConfigurationSourceProviderId(PROVIDER);
  step.setRepositoryUrl("https://github.com/example/network.git");
  var details=step.spec().toDetails(null);
  var source=(CreateGitConfigSourceDetails)details.getConfigSource();
  assertFalse(source.wasPropertyExplicitlySet("branchName"));
  assertFalse(source.wasPropertyExplicitlySet("workingDirectory"));
  assertFalse(details.wasPropertyExplicitlySet("variables"));
  assertFalse(details.wasPropertyExplicitlySet("description"));
 }
}
