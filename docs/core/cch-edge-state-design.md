# Edge-State CCH Design For Turn Costs

This note defines the v2 model for edge-based `graphhopper-cch`. It is the design boundary for adding turn costs while
keeping the existing node-based CCH implementation compatible.

## State Model

Node-based CCH nodes remain GraphHopper tower nodes. Edge-based CCH uses a separate state graph:

* one CCH state represents one directed base-edge traversal, identified by a GraphHopper edge key;
* a state means "the route has traversed this directed edge and is now at the directed edge's head node";
* the state id is deterministic and maps to an edge key through explicit arrays, not by assuming every edge key is valid;
* loop base edges are excluded from v2 edge-state CCH until their turn semantics are designed explicitly.

For a base edge `u-v`, GraphHopper already defines two edge keys:

* `2 * edgeId` for storage direction;
* `2 * edgeId + 1` for reverse storage direction.

The edge-state model keeps both possible directed states in the metric-independent topology so topology can be reused
across profiles. Profile access, one-way restrictions, and turn restrictions decide which transitions get finite metrics.

## Transition Arcs

An edge-state CCH input arc represents a transition from an incoming directed edge state to an outgoing directed edge
state at their shared via node:

```text
state(inEdgeKey: a -> via)  ->  state(outEdgeKey: via -> b)
```

The transition metric is:

```text
weight = weighting.calcTurnWeight(inEdgeId, viaNode, outEdgeId)
       + weighting.calcEdgeWeight(outEdgeState, outReverse)

millis = weighting.calcTurnMillis(inEdgeId, viaNode, outEdgeId)
       + weighting.calcEdgeMillis(outEdgeState, outReverse)

distance = outEdgeState.getDistance()
```

The first edge of a route is not represented by a core transition. It is injected by request-local boundary logic:

* tower or virtual source boundary arcs pay the first outgoing edge traversal cost and enter the corresponding edge
  state;
* target boundary handling accepts any edge state whose head reaches the target and adds zero core cost, plus any
  virtual target segment cost when needed.

This keeps persisted edge-state topology independent from request endpoints.

## Topology And Ordering

Edge-based CCH uses a separate topology from node-based CCH:

* input graph nodes are edge states;
* directed input arcs are turn transitions;
* undirected support edges connect the two endpoint states of every possible transition;
* fill edges and triangle enumeration follow the same CCH topology rules as node-based CCH;
* ordering can be lifted from a base-node order by ranking each edge state by the rank of its head/via node, then by
  edge key and state id.

This lift makes edge-state ordering deterministic and aligned with node separators, but it is still a simple baseline:
it can improve elimination-tree shape without guaranteeing lower fill than edge-id order. High-quality edge-state
ordering remains a performance-tuning problem.

The topology includes all graph-level possible state transitions, even when a profile later makes them inaccessible. This
preserves metric independence and allows profile-specific one-way access and turn restrictions to live only in metric
customization.

## Metric And Provenance Boundary

`CCHMetricSource` remains the customization boundary. v2 adds an edge-based source that emits candidates for transition
arcs. The current direct provenance shape is enough for node-based edge traversal, but edge-based unpacking needs richer
direct provenance:

* incoming edge key;
* via node;
* outgoing edge key;
* outgoing base edge id and direction;
* turn weight/millis and edge weight/millis split for path totals and diagnostics.

Shortcut provenance remains skipped CCH arc pairs. Unpacking recursively expands shortcuts until it reaches direct
edge-transition provenance, then emits only the outgoing base edge segment. The source boundary emits the first edge
segment separately through the transient boundary overlay.

## Persistence

Node-based persisted data remains unchanged. Edge-based CCH must use separate storage names and headers:

* `cch_edge_topology`;
* `cch_edge_metric_<profile>`.

Edge-based headers must include:

* storage version and mode `EDGE_BASED`;
* base graph node count and base edge count;
* edge-state count and CCH arc count;
* topology fingerprint including edge-key-to-state mapping;
* profile hash and turn-cost configuration hash for metrics.

Loading node-based data as edge-based data, or edge-based data as node-based data, must fail clearly.

## QueryGraph Boundary

Persisted edge-state topology never includes `QueryGraph` virtual edges. Virtual endpoints are handled through a
request-local overlay:

* source overlay arcs enter core edge states from virtual or tower endpoints;
* target overlay arcs leave core edge states into virtual or tower endpoints;
* same split-edge direct virtual routes stay boundary-only candidates;
* core candidates that would illegally use the original split base edge are discarded, as in v1 node-based CCH.

Turn costs for virtual endpoints are evaluated at the handoff between boundary edge keys and core edge states using the
same `Weighting.calcTurnWeight` and `calcTurnMillis` calls used by flexible edge-based routing.

## Parity Strategy

Correctness remains the first v2 gate. For unique-route fixtures, stable bytes must match:

```text
route(flexible edge-based) == route(CH edge-based) == route(CCH edge-based)
```

Stable fields include found/errors, weight, time, distance, edge ids, edge keys, points, waypoints, waypoint indices,
derived node sequence, and instructions when enabled.

Non-unique randomized graphs should compare shortest weight and legal path behavior instead of byte-identical edge
sequence.

Required test scenarios:

* one-way and reverse-only access;
* finite u-turn costs;
* infinite u-turn restrictions;
* no-left and no-right restrictions;
* turn restrictions at virtual endpoints;
* disconnected/no-path cases;
* persisted reload parity.

## Out Of Scope

The v2 edge-state design does not include:

* native RoutingKit dependency;
* perfect customization;
* partial metric updates;
* loop-edge turn semantics;
* native external nested-dissection integration for edge-state CCH;
* performance tuning beyond correctness-safe smoke metrics.
