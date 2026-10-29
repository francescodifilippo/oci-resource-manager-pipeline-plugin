package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.Job;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import com.oracle.bmc.resourcemanager.requests.CreateJobRequest;
import com.oracle.bmc.resourcemanager.requests.GetJobRequest;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import com.oracle.bmc.resourcemanager.responses.CreateJobResponse;
import com.oracle.bmc.resourcemanager.responses.GetJobResponse;
import com.oracle.bmc.resourcemanager.responses.ListStacksResponse;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory ResourceManager: listStacks returns the configured pages as given (pages linked by opc-next-page
 * tokens "p1", "p2", ...), and every created job has already succeeded. Other calls fail.
 */
final class FakeResourceManager {
 final List<List<StackSummary>> pages=new CopyOnWriteArrayList<>();
 final List<ListStacksRequest> listRequests=new CopyOnWriteArrayList<>();
 final List<CreateJobRequest> createRequests=new CopyOnWriteArrayList<>();
 final AtomicInteger closed=new AtomicInteger();

 @SafeVarargs static FakeResourceManager withPages(List<StackSummary>... pages){
  FakeResourceManager fake=new FakeResourceManager();fake.pages.addAll(List.of(pages));return fake;
 }
 static StackSummary stack(String id,String name){return StackSummary.builder().id(id).displayName(name).build();}

 ResourceManager client(){
  return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},
   (proxy,method,args)->switch(method.getName()){
    case "listStacks" -> listStacks((ListStacksRequest)args[0]);
    case "createJob" -> createJob((CreateJobRequest)args[0]);
    case "getJob" -> GetJobResponse.builder().job(succeeded(((GetJobRequest)args[0]).getJobId())).build();
    case "close" -> {closed.incrementAndGet();yield null;}
    case "hashCode" -> System.identityHashCode(proxy);
    case "equals" -> proxy==args[0];
    case "toString" -> "FakeResourceManager";
    default -> throw new UnsupportedOperationException(method.getName());
   });
 }
 private ListStacksResponse listStacks(ListStacksRequest request){
  listRequests.add(request);
  int i=request.getPage()==null?0:Integer.parseInt(request.getPage().substring(1));
  return ListStacksResponse.builder().items(pages.isEmpty()?List.of():pages.get(i))
    .opcNextPage(i+1<pages.size()?"p"+(i+1):null).build();
 }
 private CreateJobResponse createJob(CreateJobRequest request){
  createRequests.add(request);
  return CreateJobResponse.builder().job(succeeded("ocid1.ormjob.oc1..job"+createRequests.size())).build();
 }
 private static Job succeeded(String id){return Job.builder().id(id).lifecycleState(Job.LifecycleState.Succeeded).build();}
}
