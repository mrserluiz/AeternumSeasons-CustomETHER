package io.github.mrserluiz.ethercraft;

import java.util.Map;

final class PortalLinks {
    private PortalLinks() {}
    static void rebind(Map<String, String> links, String source, String target) {
        if (source.equals(target)) throw new IllegalArgumentException("Um portal não pode vincular a si mesmo.");
        disconnect(links, source);
        disconnect(links, target);
        links.put(source, target);
        links.put(target, source);
    }
    static boolean newer(Iterable<String> registrationOrder, String source, String previous) {
        boolean sawPrevious = false;
        for (String key : registrationOrder) {
            if (key.equals(previous)) sawPrevious = true;
            if (key.equals(source)) return sawPrevious;
        }
        return false;
    }
    private static void disconnect(Map<String, String> links, String key) {
        String previous = links.remove(key);
        if (previous != null && key.equals(links.get(previous))) links.remove(previous);
    }
}
