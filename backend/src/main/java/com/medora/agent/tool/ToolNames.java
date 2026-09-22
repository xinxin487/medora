package com.medora.agent.tool;

public final class ToolNames {

    public static final String QUERY_DEPARTMENTS = "queryDepartments";
    public static final String QUERY_DOCTORS = "queryDoctors";
    public static final String QUERY_SCHEDULE_SLOTS = "queryScheduleSlots";
    public static final String GET_USER_APPOINTMENTS = "getUserAppointments";
    public static final String PREPARE_APPOINTMENT = "prepareAppointment";
    /** @deprecated use PREPARE_APPOINTMENT; this aliases the new external tool name. */
    @Deprecated public static final String BOOK_APPOINTMENT = PREPARE_APPOINTMENT;
    public static final String CANCEL_APPOINTMENT = "cancelAppointment";
    public static final String SEARCH_KNOWLEDGE = "searchKnowledge";

    private ToolNames() {
    }
}
