package io.jenkins.plugins.iac.ocirm;

import com.oracle.bmc.resourcemanager.ResourceManager;
import com.oracle.bmc.resourcemanager.model.Job;
import com.oracle.bmc.resourcemanager.model.Stack;
import com.oracle.bmc.resourcemanager.model.StackSummary;
import com.oracle.bmc.resourcemanager.requests.CreateJobRequest;
import com.oracle.bmc.resourcemanager.requests.CreateStackRequest;
import com.oracle.bmc.resourcemanager.requests.GetJobRequest;
import com.oracle.bmc.resourcemanager.requests.GetStackRequest;
import com.oracle.bmc.resourcemanager.requests.ListStacksRequest;
import com.oracle.bmc.resourcemanager.responses.CreateJobResponse;
import com.oracle.bmc.resourcemanager.responses.CreateStackResponse;
import com.oracle.bmc.resourcemanager.responses.GetJobResponse;
import com.oracle.bmc.resourcemanager.responses.GetStackResponse;
import com.oracle.bmc.resourcemanager.responses.ListStacksResponse;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory ResourceManager: listStacks returns the configured pages (pages linked by opc-next-page tokens "p1",
 * "p2", ...) followed, on the last page, by the stacks created through createStack, filtered by the requested
 * lifecycle state. Stack states follow the scripts in {@link #states}: each getStack moves a stack to its next
 * scripted state, and the last state stays. Every created job has already succeeded. Other calls fail.
 */
final class FakeResourceManager {
 final List<List<StackSummary>> pages=new CopyOnWriteArrayList<>();
 final List<ListStacksRequest> listRequests=new CopyOnWriteArrayList<>();
 final List<CreateJobRequest> createRequests=new CopyOnWriteArrayList<>();
 final List<CreateStackRequest> createStackRequests=new CopyOnWriteArrayList<>();
 /** Remaining scripted states per stack OCID; the head is the current state. */
 final Map<String,Deque<Stack.LifecycleState>> states=new ConcurrentHashMap<>();
 /** States of every stack created next; the first is the state createStack returns. */
 List<Stack.LifecycleState> createdStates=List.of(Stack.LifecycleState.Active);
 /** Stacks that appear in listStacks once a stack has been created, as if a concurrent build created them. */
 final List<StackSummary> concurrent=new CopyOnWriteArrayList<>();
 /** Leaves created stacks out of listStacks, as a list that does not show a new stack yet. */
 volatile boolean hideCreated;
 private final Map<String,StackSummary> created=Collections.synchronizedMap(new LinkedHashMap<>());
 private final Map<String,String> createdByRetryToken=new ConcurrentHashMap<>();
 final AtomicInteger closed=new AtomicInteger();

 @SafeVarargs static FakeResourceManager withPages(List<StackSummary>... pages){
  FakeResourceManager fake=new FakeResourceManager();fake.pages.addAll(List.of(pages));return fake;
 }
 static StackSummary stack(String id,String name){return StackSummary.builder().id(id).displayName(name).build();}
 static StackSummary stack(String id,String name,Stack.LifecycleState state){
  return StackSummary.builder().id(id).displayName(name).lifecycleState(state).build();
 }
 FakeResourceManager script(String stackId,Stack.LifecycleState... script){states.put(stackId,new ArrayDeque<>(List.of(script)));return this;}

 ResourceManager client(){
  return (ResourceManager)Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),new Class<?>[]{ResourceManager.class},
   (proxy,method,args)->switch(method.getName()){
    case "listStacks" -> listStacks((ListStacksRequest)args[0]);
    case "createStack" -> createStack((CreateStackRequest)args[0]);
    case "getStack" -> getStack((GetStackRequest)args[0]);
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
  boolean last=i+1>=pages.size();
  List<StackSummary> items=new ArrayList<>();
  if(!pages.isEmpty())for(StackSummary s:pages.get(i))items.add(withState(s,current(s.getId(),s.getLifecycleState())));
  if(last){
   if(!hideCreated)synchronized(created){for(StackSummary s:created.values())items.add(withState(s,current(s.getId(),null)));}
   if(!created.isEmpty())items.addAll(concurrent);
  }
  // A configured stack without a state matches any state filter.
  if(request.getLifecycleState()!=null)
   items.removeIf(s->s.getLifecycleState()!=null&&s.getLifecycleState()!=request.getLifecycleState());
  return ListStacksResponse.builder().items(items).opcNextPage(last?null:"p"+(i+1)).build();
 }
 private CreateStackResponse createStack(CreateStackRequest request){
  createStackRequests.add(request);
  String id=createdByRetryToken.computeIfAbsent(request.getOpcRetryToken(),token->{
   String newId="ocid1.ormstack.oc1..created"+(created.size()+1);
   created.put(newId,StackSummary.builder().id(newId).displayName(request.getCreateStackDetails().getDisplayName())
     .compartmentId(request.getCreateStackDetails().getCompartmentId()).build());
   states.put(newId,new ArrayDeque<>(createdStates));
   return newId;
  });
  return CreateStackResponse.builder().stack(Stack.builder().id(id).lifecycleState(current(id,null)).build()).build();
 }
 private GetStackResponse getStack(GetStackRequest request){
  String id=request.getStackId();
  Deque<Stack.LifecycleState> script=states.get(id);
  if(script!=null&&script.size()>1)script.removeFirst();
  return GetStackResponse.builder().stack(Stack.builder().id(id).lifecycleState(current(id,null)).build()).build();
 }
 private Stack.LifecycleState current(String id,Stack.LifecycleState otherwise){
  Deque<Stack.LifecycleState> script=states.get(id);
  return script==null||script.isEmpty()?otherwise:script.peekFirst();
 }
 private static StackSummary withState(StackSummary s,Stack.LifecycleState state){
  return StackSummary.builder().copy(s).lifecycleState(state).build();
 }
 List<String> createdIds(){synchronized(created){return List.copyOf(created.keySet());}}
 private CreateJobResponse createJob(CreateJobRequest request){
  createRequests.add(request);
  return CreateJobResponse.builder().job(succeeded("ocid1.ormjob.oc1..job"+createRequests.size())).build();
 }
 private static Job succeeded(String id){return Job.builder().id(id).lifecycleState(Job.LifecycleState.Succeeded).build();}
}
