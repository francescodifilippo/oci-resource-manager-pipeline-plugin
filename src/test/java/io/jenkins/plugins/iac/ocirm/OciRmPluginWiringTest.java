package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;

import hudson.ExtensionList;
import io.jenkins.plugins.iac.core.IacBackend;
import java.util.Set;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OciRmPluginWiringTest {
 @Test
 void registersBackendAndPipelineSteps(JenkinsRule jenkins) {
  assertNotNull(jenkins.jenkins);
  assertEquals("oci-rm",IacBackend.find("oci-rm").id());
  Set<String> names=ExtensionList.lookup(StepDescriptor.class).stream()
    .map(StepDescriptor::getFunctionName).collect(Collectors.toSet());
  assertTrue(names.contains("ociRmProvision"));
  assertTrue(names.contains("ociRmAwait"));
  assertNotNull(OciRmConnections.get());
 }
}
