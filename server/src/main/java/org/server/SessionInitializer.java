package org.server;

@FunctionalInterface
public interface SessionInitializer {
  Session initializeSession(Session.Side side);
}
