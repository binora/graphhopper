# Customizable Contraction Hierarchies

The `graphhopper-cch` module adds an experimental Customizable Contraction Hierarchies (CCH) routing mode for GraphHopper.
CCH separates metric-independent topology preparation from per-profile metric customization, so one prepared topology can
be reused with one customized metric per `profiles_cch` profile.

## Enable CCH

CCH is provided by the separate `CCHGraphHopper` adapter. Plain `GraphHopper` does not use `profiles_cch` unless the
application is wired to instantiate `CCHGraphHopper` and `CCHGraphHopperConfig`.

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

Each `profiles_cch` entry references a normal routing profile by name. The referenced profile must be node-based and
must not use turn costs.

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

## v1 Scope

The first version supports node-based directed CCH without turn costs. It stores graph-level CCH topology and one
customized metric per CCH profile. It supports tower-node requests and normal coordinate requests that snap to
`QueryGraph` virtual endpoints.

Supported CCH routes are expected to match flexible and CH routing for stable fields such as found/errors, weight, time,
distance, edge ids, edge keys, points, waypoints, and instructions when enabled.

## Current Limitations

The following features are intentionally out of scope for v1:

* turn costs and edge-based CCH;
* headings, pass-through routing, curbsides, round trips, alternative routes, and per-request custom models;
* perfect customization and partial metric updates;
* native RoutingKit as a runtime dependency.

Requests using unsupported features should either disable CCH with `customizable_ch.disable=true` or use an existing
CH, LM, or flexible profile that supports the requested behavior.
