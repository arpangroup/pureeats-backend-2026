package com.pureeats.geo.index;

import com.pureeats.geo.LatLng;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Function;

/**
 * A real, working 2D KD-Tree - a pure in-memory data structure with no external dependency, so
 * (unlike {@code RoutingEngine}) there's nothing to fake here. Built once from a caller-supplied
 * candidate list (e.g. delivery-boundary centroids), then supports fast k-nearest-neighbor queries
 * without testing every candidate for every lookup.
 * <p>
 * Immutable once built - if the underlying candidate set changes (a new delivery boundary is drawn,
 * one is deleted), the caller rebuilds a new {@code KDTree} rather than mutating this one in place.
 */
public final class KDTree<T> {

    private final Node root;

    public KDTree(List<T> points, Function<T, LatLng> locationOf) {
        List<Indexed> indexed = points.stream().map(p -> {
            LatLng at = locationOf.apply(p);
            return new Indexed(p, Double.parseDouble(at.lat()), Double.parseDouble(at.lng()));
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        this.root = build(indexed, 0);
    }

    public Optional<T> nearest(double lat, double lng) {
        return kNearest(lat, lng, 1).stream().findFirst();
    }

    /** Returns up to {@code k} candidates, closest first. Fewer than {@code k} if the tree holds fewer points. */
    public List<T> kNearest(double lat, double lng, int k) {
        if (root == null || k <= 0) {
            return List.of();
        }
        PriorityQueue<NodeDistance> best = new PriorityQueue<>(Comparator.comparingDouble((NodeDistance nd) -> nd.distSq).reversed());
        search(root, lat, lng, 0, k, best);
        List<T> result = new ArrayList<>(best.size());
        while (!best.isEmpty()) {
            result.add(0, best.poll().node.value);
        }
        return result;
    }

    private void search(Node node, double lat, double lng, int depth, int k, PriorityQueue<NodeDistance> best) {
        if (node == null) {
            return;
        }
        double distSq = distSq(node, lat, lng);
        if (best.size() < k) {
            best.offer(new NodeDistance(node, distSq));
        } else if (distSq < best.peek().distSq) {
            best.poll();
            best.offer(new NodeDistance(node, distSq));
        }

        int axis = depth % 2;
        double diff = axis == 0 ? lat - node.lat : lng - node.lng;
        Node near = diff <= 0 ? node.left : node.right;
        Node far = diff <= 0 ? node.right : node.left;

        search(near, lat, lng, depth + 1, k, best);

        // Only descend into the far side if it could still contain something closer than our
        // current worst kept candidate - the whole point of a KD-Tree over a linear scan.
        if (best.size() < k || diff * diff < best.peek().distSq) {
            search(far, lat, lng, depth + 1, k, best);
        }
    }

    private double distSq(Node node, double lat, double lng) {
        double dLat = node.lat - lat;
        double dLng = node.lng - lng;
        return dLat * dLat + dLng * dLng;
    }

    private Node build(List<Indexed> points, int depth) {
        if (points.isEmpty()) {
            return null;
        }
        int axis = depth % 2;
        points.sort(axis == 0 ? Comparator.comparingDouble(p -> p.lat) : Comparator.comparingDouble(p -> p.lng));
        int mid = points.size() / 2;
        Indexed pivot = points.get(mid);
        Node node = new Node(pivot.value, pivot.lat, pivot.lng);
        node.left = build(new ArrayList<>(points.subList(0, mid)), depth + 1);
        node.right = build(new ArrayList<>(points.subList(mid + 1, points.size())), depth + 1);
        return node;
    }

    private final class Node {
        final T value;
        final double lat;
        final double lng;
        Node left;
        Node right;

        Node(T value, double lat, double lng) {
            this.value = value;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private final class NodeDistance {
        final Node node;
        final double distSq;

        NodeDistance(Node node, double distSq) {
            this.node = node;
            this.distSq = distSq;
        }
    }

    private final class Indexed {
        final T value;
        final double lat;
        final double lng;

        Indexed(T value, double lat, double lng) {
            this.value = value;
            this.lat = lat;
            this.lng = lng;
        }
    }
}
