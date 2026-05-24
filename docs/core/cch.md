# Customizable Contraction Hierarchies

The `graphhopper-cch` module adds experimental Customizable Contraction Hierarchies (CCH) support for GraphHopper.
CCH separates metric-independent topology preparation from per-profile metric customization. For node-based profiles this
means one prepared graph-level topology can be reused with one customized metric per `profiles_cch` profile. The module
also contains the v2 edge-state CCH core for turn-cost correctness, but the public `CCHGraphHopper` adapter still exposes
only node-based `profiles_cch` routing.

## Enable CCH

CCH is provided by the separate `CCHGraphHopper` adapter. Embedded applications that instantiate plain `GraphHopper`
directly do not use `profiles_cch`; use `CCHGraphHopper` and `CCHGraphHopperConfig` there. The standard Dropwizard
server starts `CCHGraphHopper` automatically when the YAML config contains non-empty `profiles_cch`. `profiles_cch`
cannot currently be combined with `gtfs.file`.

Programmatic setup:

```java
CCHGraphHopper hopper = new CCHGraphHopper();
hopper.setProfiles(...);
hopper.setCCHProfiles(new CCHProfile("car"));
```

Configuration setup with `CCHGraphHopperConfig`:

```yaml
profiles:
  - name: car
    custom_model_files: [car.json]

profiles_cch:
  - profile: car
```

Each `profiles_cch` entry references a normal routing profile by name. In the current `CCHGraphHopper` adapter the
referenced profile must be node-based and must not use turn costs. Profiles with `turn_costs` should stay configured for
CH, LM, or flexible routing until the edge-state CCH adapter is wired into `profiles_cch`.

Turn-cost profile configuration remains the normal GraphHopper profile configuration:

```yaml
profiles:
  - name: car_turn_costs
    custom_model_files: [car.json]
    turn_costs:
      vehicle_types: [car]
      u_turn_costs: 60

profiles_ch:
  - profile: car_turn_costs

# Do not list car_turn_costs in profiles_cch with the current adapter.
```

## Request Selection

For profiles listed in `profiles_cch`, CCH is selected before CH, LM, or flexible routing. To skip CCH for one request,
set:

```text
customizable_ch.disable=true
```

After CCH is disabled, normal GraphHopper selection applies:

* CH is used when available unless `ch.disable=true` is set.
* LM is used when available unless `lm.disable=true` is set.
* Flexible routing is used when both CH and LM are disabled or unavailable.

The existing `ch.disable` and `lm.disable` parameters do not disable CCH directly.

## Admin Metric Recustomization And Traffic Snapshots

CCH metric customization normally runs during `importOrLoad()` and reloads from persisted metric generations. For
configured `profiles_cch` profiles the server also exposes a synchronous admin recustomization endpoint:

```text
GET  /cch/customize
POST /cch/customize/{profile}
```

`GET /cch/customize` returns one status object per CCH profile, including the active metric generation, profile hash,
topology fingerprint, node count, and arc count. `POST /cch/customize/{profile}` rebuilds only that profile's metric
against the already prepared graph-level topology. The topology/order is not rebuilt. The new metric is written as a new
generation, the active generation property is flushed after the metric file, and runtime routing switches to the new
immutable `RoutingCCHGraph` only after persistence succeeds.

Only one recustomization can run at a time. Concurrent requests return HTTP `409 Conflict`. Recustomization requires
write access; read-only reloads can route with persisted CCH but cannot replace metrics. This endpoint does not reload
YAML, does not accept per-request `custom_model` payloads, and does not implement partial traffic updates. It is an
operational hook for rebuilding the current configured profile metric without rebuilding the graph-level CCH topology.

The same recustomization path can consume an active traffic snapshot. Traffic snapshots are immutable request-time
overlays keyed by base edge id and direction. They are not written into `BaseGraph`; they wrap the profile `Weighting`.
Each override can set:

* `speed_kmh` to replace traversal time for that directed edge;
* `delay_millis` to add delay to the directed edge;
* `blocked=true` to make that directed edge inaccessible.

Traffic endpoints are available when the server is running `CCHGraphHopper`:

```text
GET  /cch/traffic/status
POST /cch/traffic/snapshots
POST /cch/traffic/snapshots/{id}/activate
POST /cch/traffic/snapshots/{id}/activate-and-customize/{profile}
```

Example snapshot upload:

```json
{
  "id": "traffic-2026-05-24T1200",
  "created_millis": 1779614400000,
  "entries": [
    { "edge": 123, "reverse": false, "speed_kmh": 12.5 },
    { "edge": 456, "reverse": true, "delay_millis": 90000 },
    { "edge": 789, "reverse": false, "blocked": true }
  ]
}
```

Activating a snapshot changes newly created flexible weightings immediately. Existing CCH routing keeps using the old
immutable metric until a recustomization finishes. The usual live-traffic flow is:

```text
POST /cch/traffic/snapshots
POST /cch/traffic/snapshots/{id}/activate-and-customize/{profile}
```

The combined endpoint activates the stored snapshot, rebuilds only the requested CCH profile metric against the existing
topology, persists the new metric generation, and swaps runtime CCH routing after the new metric is ready. The active
snapshot metadata is persisted with the metric so reloaded virtual endpoint boundary weights use the same traffic
overlay. This is still full metric customization, not partial CCH customization; update frequency should be chosen from
measured customization time for the target map and order.

## Support Matrix

| Capability | `CCHGraphHopper` adapter | Module core |
| --- | --- | --- |
| Node-based directed routing without turn costs | Supported through `profiles_cch` | Supported |
| Tower-node endpoints | Supported | Supported |
| Coordinate requests using `QueryGraph` virtual endpoints | Supported | Supported |
| Persisted topology and per-profile metric reload | Supported for node-based CCH | Supported for node-based and edge-state storage objects |
| Admin metric recustomization for configured profiles | Supported via `/cch/customize/{profile}` and Java API | Supported for node-based metrics |
| Traffic snapshot overlays | Supported via `/cch/traffic/*` and full metric recustomization | Supported through `CCHTrafficWeighting` and metric sources |
| Turn costs and turn restrictions | Not exposed through `profiles_cch`; turn-cost profiles are rejected clearly | Supported by the edge-state v2 core |
| Edge-based CCH route query/unpacking | Not exposed through `CCHGraphHopper` yet | Supported by `EdgeStateCCHInputGraph`, `EdgeStateCCHTopology`, `EdgeBasedCCHMetricSource`, `EdgeBasedCCHQuery`, and `EdgeBasedCCHPathUnpacker` |
| Native RoutingKit dependency | Not used | Not used |
| Perfect customization or partial metric updates | Not supported | Not supported |

Supported application-facing CCH routes are expected to match flexible and CH routing for stable fields such as
found/errors, weight, time, distance, edge ids, edge keys, points, waypoints, and instructions when enabled.

For edge-state v2 correctness tests, the parity contract is:

```text
route(flexible edge-based) == route(CH edge-based) == route(CCH edge-based)
```

The v2 edge-state core includes one-way access, finite u-turn costs, infinite turn restrictions, virtual endpoint turn
costs, persisted edge topology/metrics, and randomized correctness coverage. It is a core API and test-backed
implementation boundary, not yet a `profiles_cch` adapter path.

## Module API Boundaries

The stable application-facing entry points for node-based CCH are `CCHGraphHopper`, `CCHGraphHopperConfig`,
`CCHProfile`, `RoutingCCHGraph`, and `CCHGraphHopper.recustomizeCCHProfile(String)`. Applications should enable CCH
through these types instead of replacing `BaseGraph` or implementing GraphHopper's `Graph` interface with a CCH overlay.

The node order is pluggable through `CCHNodeOrderProvider`. The default provider is deterministic and portable, but it
is not a high-quality nested-dissection order. For performance experiments, export the CCH support graph and import an
offline order:

```java
CCHInputGraph supportGraph = BaseGraphCCHSupportBuilder.fromGraph(hopper.getBaseGraph());
CCHOrderIO.writeMetisGraph(supportGraph, Path.of("car-cch.graph"));

// Run an external orderer such as ndmetis, KaHIP node_ordering, or InertialFlowCutter offline.
hopper.setCCHNodeOrderProvider(new FileCCHNodeOrderProvider(Path.of("car-cch.order")));
```

The imported order file is a whitespace-separated list of original node ids in increasing rank order. The default file
provider expects zero-based GraphHopper node ids; use `FileCCHNodeOrderProvider.Numbering.ONE_BASED` for one-based
orders. The module validates the imported order as a full permutation before preparing CCH.

For a portable in-process baseline, `CoordinateNestedDissectionCCHNodeOrderProvider` computes a deterministic recursive
spatial separator order from GraphHopper node coordinates. It is useful for tests, small maps, and comparing against the
degree-order fallback, but external FlowCutter/KaHIP/METIS orders should be preferred for serious production
performance experiments.

The extension boundary is `CCHMetricSource`. Metric customization consumes deterministic metric candidates rather than
raw `BaseGraph` edges, which keeps the node-based adapter separate from edge-state and turn-cost sources. The edge-state
model is described in [Edge-State CCH Design For Turn Costs](./cch-edge-state-design.md).

The topology, customization, triangle enumeration, query, unpacking, boundary-overlay, and persistence classes are
public for module composition and tests, but they are not yet a compatibility promise for external applications. Treat
them as construction and diagnostic APIs until the edge-based adapter API is promoted.

## Current Limitations

The following features are intentionally out of scope for the current public adapter:

* headings, pass-through routing, curbsides, round trips, alternative routes, and per-request custom models;
* `profiles_cch` turn-cost profile routing;
* per-request traffic payloads;
* perfect customization and partial metric updates;
* native RoutingKit as a runtime dependency.

Requests using unsupported features should either disable CCH with `customizable_ch.disable=true` or use an existing
CH, LM, or flexible profile that supports the requested behavior.
