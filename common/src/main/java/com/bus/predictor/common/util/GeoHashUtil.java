package com.bus.predictor.common.util;

public class GeoHashUtil {

    private static final char[] BASE32_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'b', 'c', 'd', 'e', 'f', 'g',
            'h', 'j', 'k', 'm', 'n', 'p', 'q', 'r',
            's', 't', 'u', 'v', 'w', 'x', 'y', 'z'
    };

    private static final int[] BITS = {16, 8, 4, 2, 1};

    public static String encode(double latitude, double longitude, int precision) {
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("Precision must be between 1 and 12");
        }

        StringBuilder geoHash = new StringBuilder();
        boolean[] bits = new boolean[5];
        int bitCount = 0;
        int charIndex = 0;

        double[] latRange = {-90.0, 90.0};
        double[] lonRange = {-180.0, 180.0};

        while (geoHash.length() < precision) {
            if (bitCount % 2 == 0) {
                double mid = (lonRange[0] + lonRange[1]) / 2;
                if (longitude >= mid) {
                    bits[charIndex] = true;
                    lonRange[0] = mid;
                } else {
                    bits[charIndex] = false;
                    lonRange[1] = mid;
                }
            } else {
                double mid = (latRange[0] + latRange[1]) / 2;
                if (latitude >= mid) {
                    bits[charIndex] = true;
                    latRange[0] = mid;
                } else {
                    bits[charIndex] = false;
                    latRange[1] = mid;
                }
            }

            charIndex++;
            bitCount++;

            if (charIndex == 5) {
                int val = 0;
                for (int i = 0; i < 5; i++) {
                    if (bits[i]) {
                        val += BITS[i];
                    }
                }
                geoHash.append(BASE32_CHARS[val]);
                charIndex = 0;
                java.util.Arrays.fill(bits, false);
            }
        }

        return geoHash.toString();
    }

    public static String encode6(double latitude, double longitude) {
        return encode(latitude, longitude, 6);
    }

    public static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
