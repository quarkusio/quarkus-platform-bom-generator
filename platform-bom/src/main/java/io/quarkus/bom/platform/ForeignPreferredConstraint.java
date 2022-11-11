package io.quarkus.bom.platform;

/**
 * In case version constraint preferences are configured, this enum will be used to
 * indicates what should be done in case a platform member includes a version constraint
 * that appears to be preferred over one provided by another platform member that owns the right to enforce
 * it on other members.
 */
public enum ForeignPreferredConstraint {

    // warn but respect the ownership by the other member
    WARN(0x001),
    // fail the process
    ERROR(0x010),
    // accept the constraint that appears satisfy configured version preferences, in case it "seems" compatible
    ACCEPT_IF_COMPATIBLE(0x100);

    public static boolean isAcceptIfCompatible(int flags) {
        return (flags & ForeignPreferredConstraint.ACCEPT_IF_COMPATIBLE.flag) > 0;
    }

    public static boolean isWarn(int flags) {
        return (flags & ForeignPreferredConstraint.WARN.flag) > 0;
    }

    public static boolean isError(int flags) {
        return (flags & ForeignPreferredConstraint.ERROR.flag) > 0;
    }

    private int flag;

    ForeignPreferredConstraint(int flag) {
        this.flag = flag;
    }

    public int flag() {
        return flag;
    }
}
