// SPDX-License-Identifier: Apache-2.0

package com.graphhopper.cch;

import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.details.PathDetail;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.graphhopper.util.Parameters.Details.EDGE_ID;
import static com.graphhopper.util.Parameters.Details.EDGE_KEY;

final class CCHStableRoute {
    final LinkedHashMap<String, String> fields;
    final String serialized;
    final byte[] bytes;

    private CCHStableRoute(LinkedHashMap<String, String> fields) {
        this.fields = fields;
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        serialized = builder.toString();
        bytes = serialized.getBytes(StandardCharsets.UTF_8);
    }

    static CCHStableRoute from(BaseGraph graph, int sourceNode, boolean instructionsEnabled, GHResponse response) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("hasErrors", Boolean.toString(response.hasErrors()));
        fields.put("errors", errors(response));
        fields.put("responsePaths", Integer.toString(response.getAll().size()));
        if (response.hasErrors())
            return new CCHStableRoute(fields);

        ResponsePath path = response.getBest();
        fields.put("weight", Double.toString(path.getRouteWeight()));
        fields.put("time", Long.toString(path.getTime()));
        fields.put("distance", Double.toString(path.getDistance()));
        fields.put("points", points(path.getPoints()));
        fields.put("waypoints", points(path.getWaypoints()));
        fields.put("waypointIndices", path.getWaypointIndices().toString());
        fields.put("edgeIds", details(path, EDGE_ID).toString());
        List<Integer> edgeKeys = details(path, EDGE_KEY);
        fields.put("edgeKeys", edgeKeys.toString());
        fields.put("nodes", nodes(graph, sourceNode, edgeKeys).toString());
        fields.put("instructions", instructionsEnabled ? instructions(path.getInstructions()) : "<disabled>");
        return new CCHStableRoute(fields);
    }

    static String firstDifferingField(CCHStableRoute expected, CCHStableRoute actual) {
        for (String key : expected.fields.keySet()) {
            String expectedValue = expected.fields.get(key);
            String actualValue = actual.fields.get(key);
            if (!expectedValue.equals(actualValue))
                return key + " expected=" + expectedValue + " actual=" + actualValue;
        }
        for (String key : actual.fields.keySet()) {
            if (!expected.fields.containsKey(key))
                return key + " only present in actual";
        }
        return "<serialized-bytes>";
    }

    private static String errors(GHResponse response) {
        if (!response.hasErrors())
            return "[]";
        List<String> errors = new ArrayList<>();
        for (Throwable error : response.getErrors()) {
            errors.add(error.getClass().getName() + ":" + error.getMessage());
        }
        return errors.toString();
    }

    private static List<Integer> details(ResponsePath path, String key) {
        List<PathDetail> details = path.getPathDetails().get(key);
        if (details == null)
            return Collections.emptyList();
        List<Integer> values = new ArrayList<>();
        for (PathDetail detail : details) {
            for (int i = detail.getFirst(); i < detail.getLast(); i++) {
                values.add((Integer) detail.getValue());
            }
        }
        return values;
    }

    private static List<Integer> nodes(BaseGraph graph, int sourceNode, List<Integer> edgeKeys) {
        List<Integer> nodes = new ArrayList<>();
        if (edgeKeys.isEmpty()) {
            nodes.add(sourceNode);
            return nodes;
        }
        for (int i = 0; i < edgeKeys.size(); i++) {
            EdgeIteratorState edge = graph.getEdgeIteratorStateForKey(edgeKeys.get(i));
            if (i == 0)
                nodes.add(edge.getBaseNode());
            nodes.add(edge.getAdjNode());
        }
        return nodes;
    }

    private static String points(PointList points) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < points.size(); i++) {
            if (i > 0)
                builder.append(',');
            builder.append(points.getLat(i)).append(':').append(points.getLon(i));
        }
        return builder.append(']').toString();
    }

    private static String instructions(InstructionList instructions) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < instructions.size(); i++) {
            if (i > 0)
                builder.append(',');
            Instruction instruction = instructions.get(i);
            builder.append('{')
                    .append("sign=").append(instruction.getSign())
                    .append(",name=").append(instruction.getName())
                    .append(",distance=").append(instruction.getDistance())
                    .append(",time=").append(instruction.getTime())
                    .append(",points=").append(points(instruction.getPoints()))
                    .append('}');
        }
        return builder.append(']').toString();
    }
}
