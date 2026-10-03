package io.github.mrserluiz.ethercraft;

public final class PortalCoordinates {
    private PortalCoordinates() {}
    public static double scale(double coordinate, double sourceScale, double destinationScale) {
        if (!Double.isFinite(coordinate) || !Double.isFinite(sourceScale) || !Double.isFinite(destinationScale)
            || sourceScale <= 0 || destinationScale <= 0) throw new IllegalArgumentException("Escala/coordenada de dimensão inválida.");
        double result = coordinate * sourceScale / destinationScale;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Coordenada de destino inválida.");
        return result;
    }
    public static double clamp(double coordinate, double center, double size, double margin) {
        double half = Math.max(0, size / 2 - margin);
        return Math.max(center - half, Math.min(center + half, coordinate));
    }
    public static boolean nearby(double x, double z, double targetX, double targetZ, int radius) {
        return Math.abs(x - targetX) <= radius && Math.abs(z - targetZ) <= radius;
    }
}
