package com.pharmaops.audit;

public class RequestContext {
    private static final ThreadLocal<String> ACTOR = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    public static void setActor(String actor) { ACTOR.set(actor); }
    public static String getActor() { return ACTOR.get(); }

    public static void setRequestId(String requestId) { REQUEST_ID.set(requestId); }
    public static String getRequestId() { return REQUEST_ID.get(); }

    public static void clear() {
        ACTOR.remove();
        REQUEST_ID.remove();
    }
}
