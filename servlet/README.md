# Jakarta Servlet with distributable HttpSession

The example in this module deploys a distributable application to a cluster of 3 application servers.
The application contains a simple servlet that performs thread-safe incrementing of a counter, with an initial value of zero, persisted within the HttpSession.
Each request increments the counter and records the value, which should be unique to the request, to the response header.
The test harness performs N requests from M clients, and records the value recorded by each response header.
After all requests have complete, the current value of the counter is checked on each cluster member.
When complete, we expect:

* No failed requests
* No lost sessions or session attributes
* Each client should have recorded N distinct values
* The final counter value should be the same on each server, i.e. N.

The test servlet comes in 4 flavours:

<dl>
<dt>SessionIdentifierMutexCounterTestCase</dt>
<dd>Uses the session identifier as a mutex to ensure thread safe reading/writing of an immutable Integer counter.</dd>
<dt>SessionAttributeMutexCounterTestCase</dt>
<dd>Uses a separate session attribute mutex to ensure thread safe reading/writing of an immutable Integer counter.</dd>
<dt>AtomicCounterTestCase</dt>
<dd>Uses an AtomicInteger session attribute to atomically increment/read a counter.</dd>
<dt>CallByValueAtomicCounterTestCase</dt>
<dd>Like AtomicCounterTestCase, but performs a redundant HttpSession.setAttribute(...) to accommodate servlet containers that would otherwise not realise that a mutable session attribute was modified.</dd>
</dl>

## Usage

The test is configured by the following system properties:

<dl>
 <dt>-Dtest.container=glassfish|openliberty|payara|tomcat|wildfly</dt>
 <dd>Specifies the name of the distributed servlet container implementation.</dd>
 <dd>N.B. glassfish and payara containers are not yet functional.</dd>
 <dt>-Dtest.router=RANDOM|ROUND_ROBIN|STICKY</dt>
 <dd>Default: STICKY</dd>
 <dd>Determines how requests from a given client should be distributed to the cluster.</dd>
 <dd>
  <dl>
   <dt>STICKY</dt>
   <dd>Sends all N requests to the same server</dd>
   <dt>ROUND_ROBIN</dt>
   <dd>Send requests to each server in round-robin fashion</dd>
   <dt>RANDOM</dt>
   <dd>Sends each request to a random server</dd>
  </dl>
 </dd>
 <dt>-Dtest.invoker=CONCURRENT|SEQUENTIAL</dt>
 <dd>Default: SEQUENTIAL</dd>
 <dd>Determines the invocation strategy:</dd>
 <dd>
  <dl>
   <dt>SEQUENTIAL</dt>
   <dd>Requests per client are sent sequentially, i.e. sends next request after previous request completes.</dd>
   <dt>CONCURRENT</dt>
   <dd>Requests per client are sent concurrently, i.e. sends next request without waiting for previous request to complete.</dd>
  </dl>
 </dd>
 <dt>-Dtest.interval=</dt>
 <dd>When specified, adds an interval between requests, specified as a duration in IS0-8601 format.</dd>
 <dd>e.g. -Dtest.interval=PT0.01s</dd>
 <dd>When used in combination with <code>-Dtest.client=SEQUENTIAL</code>, requests per client are sent at a fixed interval.</dd>
 <dd>When used in combination with <code>-Dtest.client=CONCURRENT</code>, requests per client are sent at a fixed rate.</dd>
 <dt>-Dtest.clients=</dt>
 <dd>Default: 1</dd>
 <dd>Specifies the number of clients, i.e. unique sessions.</dd>
 <dt>-Dtest.requests=</dt>
 <dd>Default: 1000</dd>
 <dd>Specifies the number of requests sent per client.</dd>
 <dt>-Dtest.threads=</dt>
 <dd>Default: 10</dd>
 <dd>Specifies the number of threads per client.</dd>
 <dd>Used only by <code>-Dtest.client=CONCURRENT</code> to limit concurrency to a given session</dd>
 <dt>-Dtest.spring=hazelcast|infinispan|redis</dt>
 <dd>When specified, instruments the test web application via Spring Session using the specified session repository implementation</dd>
</dl>

e.g.

```shell
mvn clean verify -Dit.test=SessionIdentifierAtomicCounterITCase -Dtest.container=wildfly -Dtest.clients=10 -Dtest.router=RANDOM -Dtest.invoker=CONCURRENT
```
