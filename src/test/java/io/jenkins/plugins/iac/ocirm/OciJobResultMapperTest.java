package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.Job;
import org.junit.jupiter.api.Test;

class OciJobResultMapperTest {
 @Test void mapsLifecycleStates(){
  var success=OciJobResultMapper.from("job-1",Job.LifecycleState.Succeeded);
  assertTrue(success.completed());assertTrue(success.successful());assertEquals("SUCCEEDED",success.status());
  var running=OciJobResultMapper.from("job-2",Job.LifecycleState.InProgress);
  assertFalse(running.completed());assertFalse(running.successful());
  var failed=OciJobResultMapper.from("job-3",Job.LifecycleState.Failed);
  assertTrue(failed.completed());assertFalse(failed.successful());
 }
}
