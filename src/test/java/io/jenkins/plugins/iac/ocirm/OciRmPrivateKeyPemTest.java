package io.jenkins.plugins.iac.ocirm;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/** Uses fake base64 bodies: no real key material. */
class OciRmPrivateKeyPemTest {
 private static final String BODY="QUJD".repeat(40);
 private static final String PEM="-----BEGIN PRIVATE KEY-----\n"+BODY.substring(0,64)+"\n"+BODY.substring(64,128)+"\n"+
   BODY.substring(128)+"\n-----END PRIVATE KEY-----\n";

 @Test void restoresLineBreaksOfAPemPastedOnOneLine(){
  assertEquals(PEM,OciRmConnections.normalizePem(PEM.replace("\n","")));
  assertEquals(PEM,OciRmConnections.normalizePem(PEM.replace("\n"," ")));
  assertEquals(PEM,OciRmConnections.normalizePem("  "+PEM.replace("\n","\r")+"  "));
 }
 @Test void keepsLabelsWithSpaces(){
  String rsa=PEM.replace("PRIVATE KEY","RSA PRIVATE KEY");
  assertEquals(rsa,OciRmConnections.normalizePem(rsa.replace("\n","")));
 }
 @Test void leavesMultiLineAndUnrecognizedValuesUnchanged(){
  assertEquals(PEM,OciRmConnections.normalizePem(PEM));
  String indented=PEM.replace("\n","\n ");
  assertEquals(indented,OciRmConnections.normalizePem(indented));
  // Legacy encrypted PEM headers cannot be restored safely.
  String encrypted="-----BEGIN RSA PRIVATE KEY-----Proc-Type: 4,ENCRYPTED DEK-Info: AES-128-CBC,00FF "+BODY+"-----END RSA PRIVATE KEY-----";
  assertEquals(encrypted,OciRmConnections.normalizePem(encrypted));
  String mismatched="-----BEGIN PRIVATE KEY-----"+BODY+"-----END RSA PRIVATE KEY-----";
  assertEquals(mismatched,OciRmConnections.normalizePem(mismatched));
  assertEquals("not a pem",OciRmConnections.normalizePem("not a pem"));
 }
}
