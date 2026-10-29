package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.model.BmcException;
import io.jenkins.plugins.iac.core.IacHttpException;
import java.io.IOException;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;

class OciRmBackendTest {
 private final OciRmBackend backend=new OciRmBackend();

 private static BmcException service(int status){return new BmcException(status,"code","message","request-id");}

 @Test void serverThrottlingAndRequestTimeoutErrorsAreTransient(){
  for(int status:new int[]{500,502,503,504,408,429})assertTrue(backend.isTransient(service(status)),"HTTP "+status);
 }
 @Test void otherClientErrorsAreNotTransient(){
  for(int status:new int[]{400,401,403,404,409,412})assertFalse(backend.isTransient(service(status)),"HTTP "+status);
 }
 @Test void networkFailuresAndTimeoutsAreTransient(){
  BmcException network=BmcException.createClientSide("connection refused",new ConnectException("refused"),"request-id",null);
  assertTrue(network.isClientSide());
  assertTrue(backend.isTransient(network));
  BmcException timeout=new BmcException(true,"read timed out",null,"request-id");
  assertTrue(timeout.isTimeout());
  assertTrue(backend.isTransient(timeout));
 }
 @Test void fallsBackToCoreDefault(){
  assertTrue(backend.isTransient(new IOException("reset")));
  assertTrue(backend.isTransient(new IacHttpException(503,"GET","/x")));
  assertFalse(backend.isTransient(new IacHttpException(404,"GET","/x")));
  assertFalse(backend.isTransient(new IllegalArgumentException("Missing or inaccessible OCI private key credential: id")));
  assertFalse(backend.isTransient(new IllegalStateException("bug")));
 }
}
