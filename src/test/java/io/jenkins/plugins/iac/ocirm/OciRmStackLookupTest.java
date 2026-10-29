package io.jenkins.plugins.iac.ocirm;

import static io.jenkins.plugins.iac.ocirm.FakeResourceManager.stack;
import static org.junit.jupiter.api.Assertions.*;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import io.jenkins.plugins.iac.core.ResolvedTarget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OciRmStackLookupTest {
 private static final String COMPARTMENT="ocid1.compartment.oc1..example";
 private static final String NETWORK="ocid1.ormstack.oc1..network",OTHER="ocid1.ormstack.oc1..other";
 private static final Map<String,String> BY_NAME=Map.of("mode","plan","stackName","network","compartmentId",COMPARTMENT);

 private static String lookup(FakeResourceManager fake,String name){
  return new OciResourceManagerApi(fake.client()).stackIdByName(COMPARTMENT,name);
 }

 @Test void oneMatchListsActiveStacksOfTheCompartmentByName(){
  FakeResourceManager fake=FakeResourceManager.withPages(List.of(stack(NETWORK,"network")));
  assertEquals(NETWORK,lookup(fake,"network"));
  ListStacksRequest request=fake.listRequests.get(0);
  assertEquals(COMPARTMENT,request.getCompartmentId());
  assertEquals("network",request.getDisplayName());
  assertEquals(Stack.LifecycleState.Active,request.getLifecycleState());
  assertNull(request.getPage());
 }
 @Test void noMatchNamesStackAndCompartment(){
  var e=assertThrows(IllegalArgumentException.class,()->lookup(FakeResourceManager.withPages(List.of()),"network"));
  assertEquals("No active OCI Resource Manager stack named 'network' in compartment "+COMPARTMENT+
    "; check stackName and compartmentId, or use stackId",e.getMessage());
 }
 @Test void severalMatchesAreListedAndRejected(){
  var e=assertThrows(IllegalArgumentException.class,()->lookup(FakeResourceManager.withPages(
    List.of(stack(NETWORK,"network"),stack(OTHER,"network"))),"network"));
  assertEquals("2 active OCI Resource Manager stacks are named 'network' in compartment "+COMPARTMENT+
    " ["+NETWORK+", "+OTHER+"]; use stackId",e.getMessage());
 }
 @Test void comparesReturnedNamesExactly(){
  FakeResourceManager fake=FakeResourceManager.withPages(
    List.of(stack(OTHER,"Network"),stack(OTHER,"network "),stack(OTHER,"network-dev"),stack(NETWORK,"network")));
  assertEquals(NETWORK,lookup(fake,"network"));
  assertThrows(IllegalArgumentException.class,()->lookup(FakeResourceManager.withPages(
    List.of(stack(OTHER,"Network"),stack(OTHER,"network-dev"))),"network"));
 }
 @Test void readsEveryPage(){
  FakeResourceManager fake=FakeResourceManager.withPages(List.of(stack(OTHER,"Network")),List.of(),List.of(stack(NETWORK,"network")));
  assertEquals(NETWORK,lookup(fake,"network"));
  assertEquals(3,fake.listRequests.size());
  assertEquals("p1",fake.listRequests.get(1).getPage());
  assertEquals("p2",fake.listRequests.get(2).getPage());
 }
 @Test void countsMatchesAcrossPages(){
  var e=assertThrows(IllegalArgumentException.class,()->lookup(FakeResourceManager.withPages(
    List.of(stack(NETWORK,"network")),List.of(stack(OTHER,"network"))),"network"));
  assertTrue(e.getMessage().startsWith("2 active"),e.getMessage());
 }

 @Test void backendResolvesStackNameAndClosesTheClient() throws Exception {
  FakeResourceManager fake=FakeResourceManager.withPages(List.of(stack(NETWORK,"network"),stack(OTHER,"other")));
  OciRmBackend backend=new OciRmBackend();
  backend.clients=(connection,run)->{assertEquals("oci",connection);return fake.client();};
  ResolvedTarget resolved=backend.resolve("oci",null,BY_NAME,null);
  assertEquals(NETWORK,resolved.targetId());
  assertEquals(BY_NAME,resolved.parameters());
  assertEquals(1,fake.closed.get());
 }
 @Test void backendClosesTheClientWhenLookupFails(){
  FakeResourceManager fake=FakeResourceManager.withPages(List.of());
  OciRmBackend backend=new OciRmBackend();
  backend.clients=(connection,run)->fake.client();
  assertThrows(IllegalArgumentException.class,()->backend.resolve("oci",null,BY_NAME,null));
  assertEquals(1,fake.closed.get());
 }
 @Test void backendUsesStackIdWithoutCallingOci() throws Exception {
  OciRmBackend backend=new OciRmBackend();
  backend.clients=(connection,run)->{throw new AssertionError("no OCI client expected");};
  Map<String,String> parameters=Map.of("mode","apply","autoApprove","true");
  ResolvedTarget resolved=backend.resolve("oci",NETWORK,parameters,null);
  assertEquals(NETWORK,resolved.targetId());
  assertEquals(parameters,resolved.parameters());
 }
 @Test void backendRequiresStackIdOrName(){
  OciRmBackend backend=new OciRmBackend();
  backend.clients=(connection,run)->{throw new AssertionError("no OCI client expected");};
  assertThrows(IllegalArgumentException.class,()->backend.resolve("oci",null,Map.of("mode","plan","stackName","network"),null));
 }
}
