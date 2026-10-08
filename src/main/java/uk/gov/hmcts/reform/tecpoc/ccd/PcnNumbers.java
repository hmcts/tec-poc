package uk.gov.hmcts.reform.tecpoc.ccd;

import java.util.regex.Pattern;

/**
 * Penalty charge number stem and suffix. The stem is the PCN without its final digit.
 * The suffix is that digit. It is not part of the check character.
 */
public final class PcnNumbers {

    private static final Pattern FULL = Pattern.compile("^[A-Z]{2,3}[0-9]{7}[0-9A][0-9]$");
    private static final Pattern STEM = Pattern.compile("^[A-Z]{2,3}[0-9]{7}[0-9A]$");

    private PcnNumbers() {
    }

    public static boolean isFullPcn(String penaltyChargeNumber) {
        return penaltyChargeNumber != null && FULL.matcher(penaltyChargeNumber).matches();
    }

    public static String stem(String penaltyChargeNumber) {
        requireFull(penaltyChargeNumber);
        return penaltyChargeNumber.substring(0, penaltyChargeNumber.length() - 1);
    }

    public static int suffix(String penaltyChargeNumber) {
        requireFull(penaltyChargeNumber);
        return penaltyChargeNumber.charAt(penaltyChargeNumber.length() - 1) - '0';
    }

    public static String withSuffix(String pcnStem, int suffix) {
        if (pcnStem == null || !STEM.matcher(pcnStem).matches()) {
            throw new IllegalArgumentException("PCN stem is not valid");
        }
        if (suffix < 0 || suffix > 9) {
            throw new IllegalArgumentException("PCN suffix cannot exceed 9");
        }
        return pcnStem + suffix;
    }

    /**
     * First registration on a new case. The suffix is {@code 0}.
     */
    public static void requireInitialRegistration(String penaltyChargeNumber) {
        requireFull(penaltyChargeNumber);
        if (suffix(penaltyChargeNumber) != 0) {
            throw new IllegalArgumentException(
                "The first registration PCN must use suffix 0: " + penaltyChargeNumber
            );
        }
    }

    /**
     * Next registration PCN: the stem plus one more than the highest existing suffix.
     */
    public static String nextRegistrationPcn(String currentRegistrationPcn) {
        int nextSuffix = suffix(currentRegistrationPcn) + 1;
        if (nextSuffix > 9) {
            throw new IllegalArgumentException("PCN suffix cannot exceed 9");
        }
        return withSuffix(stem(currentRegistrationPcn), nextSuffix);
    }

    private static void requireFull(String penaltyChargeNumber) {
        if (!isFullPcn(penaltyChargeNumber)) {
            throw new IllegalArgumentException(
                "Penalty charge number is not valid: " + penaltyChargeNumber
            );
        }
    }
}
