package render;

import annotation.Draft;
import annotation.Intention;

/**
 * Default user role recorder. Any worker registered past the built-in roles
 * (ROLE_USER..N) routes here; custom scene types register their own recorder.
 */
@Draft
@Intention("User role recorder. Custom workers route through here into the merged frame.")
public final class UserRole {

    private UserRole() {}

    public static void record(long taskPtr) {
        // TODO(role): custom registrationtable for user recorders. Structure only.
    }
}