package org.neo4j.graphalgo.api;

import org.neo4j.graphdb.Direction;

/**
 * @author mknblch
 */
public interface HugeRelationshipIterator {

    void forEachRelationship(
            long nodeId,
            Direction direction,
            HugeRelationshipConsumer consumer);

    default void forEachIncoming(
            long nodeId,
            HugeRelationshipConsumer consumer) {
        forEachRelationship(nodeId, Direction.INCOMING, consumer);
    }

    default void forEachOutgoing(
            long nodeId,
            HugeRelationshipConsumer consumer) {
        forEachRelationship(nodeId, Direction.OUTGOING, consumer);
    }

    /**
     * Returns a copy of this iterator that reuses new cursors internally,
     * so that iterations happen independent from other iterations.
     */
    default HugeRelationshipIterator concurrentCopy() {
        return this;
    }
}
