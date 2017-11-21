package org.exoplatform.injection.services.module;


import org.exoplatform.calendar.model.Calendar;
import org.exoplatform.calendar.model.Event;
import org.exoplatform.calendar.service.*;
import org.exoplatform.injection.services.helper.InjectorUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.OrganizationService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalendarModule {

    /**
     * The log.
     */
    private final Log LOG = ExoLogger.getLogger(CalendarModule.class);

    protected CalendarService calendarService;

    protected ExtendedCalendarService extendedCalendarService;
    protected OrganizationService organizationService;

    public CalendarModule(CalendarService calendarService, ExtendedCalendarService extendedCalendarService,OrganizationService organizationService) {
        this.calendarService = calendarService;
        this.extendedCalendarService = extendedCalendarService;
        this.organizationService = organizationService;

    }

    /**
     * Sets the calendar colors.
     *
     * @param calendars the calendars
     */
    public void setCalendarColors(JSONArray calendars) {
        for (int i = 0; i < calendars.length(); i++) {
            try {
                JSONObject calendarObject = calendars.getJSONObject(i);
                String username = calendarObject.getString("user");
                JSONArray userCalendars = calendarObject.getJSONArray("calendars");
                Map<String, JSONObject> map = new HashMap();
                for (int j = 0; j < userCalendars.length(); j++) {
                    JSONObject userCalendar = userCalendars.getJSONObject(j);
                    map.put(userCalendar.getString("name"), userCalendar);
                }

                String filtered = null;
                try {
                    String[] calendarIdList = getCalendarsIdList(username);
                    for (String calId : calendarIdList) {
                        Calendar calendar = extendedCalendarService.getCalendarHandler().getCalendarById(calId);
                        String calName = calendar.getName();
                        if (map.containsKey(calName)) {
                            JSONObject calTemp = map.get(calName);
                            calendar.setCalendarColor(calTemp.getString("color"));
                            calendar.setCalendarOwner(username);
                            if (calTemp.has("type") && calTemp.getString("type").equals("user")) {
                                extendedCalendarService.getCalendarHandler().saveCalendar(calendar);
                            } else {
                                //--calendarService_.savePublicCalendar(calendar, false);
                                extendedCalendarService.getCalendarHandler().saveCalendar(calendar);
                            }
                        } else {
                            filtered = calendar.getId();
                        }
                    }
                    if (filtered != null) {
                        CalendarSetting setting = calendarService.getCalendarSetting(username);
                        setting.setFilterPublicCalendars(new String[]{filtered});
                        calendarService.saveCalendarSetting(username, setting);
                    }
                } catch (Exception e) {
                    LOG.error("Cannot create user calendar", e, e.getMessage());
                }
            } catch (JSONException e) {
                LOG.error("Syntax error on calendar n°" + i, e);

            }

        }
    }

    /**
     * Creates the events.
     *
     * @param calendars the calendars
     */
    public void createEvents(JSONArray calendars) {

        for (int i = 0; i < calendars.length(); i++) {
            try {
                JSONObject calendarObject = calendars.getJSONObject(i);
                String username = calendarObject.getString("user");
                Map<String, String> map = getCalendarsMap(username);

                if (calendarObject.has("clearAll") && calendarObject.getBoolean("clearAll")) {
                    removeAllEvents(username);
                }
                JSONArray userCalendars = calendarObject.getJSONArray("calendars");
                for (int j = 0; j < userCalendars.length(); j++) {
                    JSONObject userCalendar = userCalendars.getJSONObject(j);
                    JSONArray events = userCalendar.getJSONArray("events");
                    for (int k = 0; k < events.length(); k++) {
                        JSONObject event = events.getJSONObject(k);
                        saveEvent(username, userCalendar.has("type") && userCalendar.getString("type").equals("user"), map.get(userCalendar.getString("name")),
                                event.getString("title"), InjectorUtils.getDayAsInt(event.getString("day")),
                                InjectorUtils.getHourAsInt(event.getString("start")), InjectorUtils.getMinuteAsInt(event.getString("start")),
                                InjectorUtils.getHourAsInt(event.getString("end")),
                                InjectorUtils.getMinuteAsInt(event.getString("end")));
                    }
                }
            } catch (JSONException e) {
                LOG.error("Syntax error on calendar n°" + i, e);

            } catch (Exception e) {
                LOG.error("Cannot created event", e, e.getMessage());
            }
        }
    }

    /**
     * Save event.
     *
     * @param username    the username
     * @param isUserEvent the is user event
     * @param calId       the cal id
     * @param summary     the summary
     * @param day         the day
     * @param fromHour    the from hour
     * @param fromMin     the from min
     * @param toHour      the to hour
     * @param toMin       the to min
     * @throws Exception the exception
     */
    private void saveEvent(String username, boolean isUserEvent, String calId, String summary,
                           int day, int fromHour, int fromMin, int toHour, int toMin) throws Exception {

        CalendarEvent event = new CalendarEvent();
        event.setCalendarId(calId);
        event.setSummary(summary);
        event.setEventType(Event.TYPE_EVENT);
        event.setRepeatType(Event.RP_NOREPEAT);
        event.setPrivate(isUserEvent);
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.setTimeInMillis(calendar.getTime().getTime());
        calendar.set(java.util.Calendar.DAY_OF_WEEK, day);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, fromHour);
        calendar.set(java.util.Calendar.MINUTE, fromMin);
        event.setFromDateTime(calendar.getTime());
        calendar.set(java.util.Calendar.HOUR_OF_DAY, toHour);
        calendar.set(java.util.Calendar.MINUTE, toMin);
        event.setToDateTime(calendar.getTime());
        if (isUserEvent)
            calendarService.saveUserEvent(username, calId, event, true);
        else
            calendarService.savePublicEvent(calId, event, true);

    }

    /**
     * Removes the all events.
     *
     * @param username the username
     * @throws Exception the exception
     */
    private void removeAllEvents(String username) throws Exception {
        List<CalendarEvent> events = getEvents(username);
        for (CalendarEvent event : events) {
            if (event.isPrivate()) {
                calendarService.removeUserEvent(username, event.getCalendarId(), event.getId());
            } else {
                calendarService.removePublicEvent(event.getCalendarId(), event.getId());
            }
        }
    }

    /**
     * Gets the calendars map.
     *
     * @param username the username
     * @return the calendars map
     */
    private Map<String, String> getCalendarsMap(String username) {
        Map<String, String> map = new HashMap<String, String>();
        String[] calendarIdList = getCalendarsIdList(username);
        for (String calId : calendarIdList) {
            Calendar calendar = null;
            try {
                calendar = calendarService.getCalendarById(calId);
                String calName = calendar.getName();
                map.put(calName, calId);
            } catch (Exception e) {
            }
        }
        return map;
    }

    /**
     * Gets the calendars id list.
     *
     * @param username the username
     * @return the calendars id list
     */
    private String[] getCalendarsIdList(String username) {
        StringBuilder sb = new StringBuilder();
        List<GroupCalendarData> listgroupCalendar = null;
        List<org.exoplatform.calendar.service.Calendar> listUserCalendar = null;
        try {
            listgroupCalendar = calendarService.getGroupCalendars(getUserGroups(username), true, username);
            listUserCalendar = calendarService.getUserCalendars(username, true);
        } catch (Exception e) {
            LOG.info("Error while checking User Calendar :" + e.getMessage());
        }
        for (GroupCalendarData g : listgroupCalendar) {
            for (org.exoplatform.calendar.service.Calendar c : g.getCalendars()) {
                sb.append(c.getId()).append(",");
            }
        }
        for (org.exoplatform.calendar.service.Calendar c : listUserCalendar) {
            sb.append(c.getId()).append(",");
        }
        String[] list = sb.toString().split(",");
        return list;
    }


    /**
     * Gets the events.
     *
     * @param username the username
     * @return the events
     */
    private List<CalendarEvent> getEvents(String username) {
        String[] calList = getCalendarsIdList(username);

        EventQuery eventQuery = new EventQuery();

        eventQuery.setOrderBy(new String[]{org.exoplatform.calendar.service.Utils.EXO_FROM_DATE_TIME});

        eventQuery.setCalendarId(calList);
        List<CalendarEvent> userEvents = null;
        try {
            userEvents = calendarService.getEvents(username, eventQuery, calList);

        } catch (Exception e) {
            LOG.info("Error while checking User Events:" + e.getMessage());
        }
        return userEvents;
    }

    /**
     * Gets the user groups.
     *
     * @param username the username
     * @return the user groups
     * @throws Exception the exception
     */
    private String[] getUserGroups(String username) throws Exception {

        Object[] objs = organizationService.getGroupHandler().findGroupsOfUser(username).toArray();
        String[] groups = new String[objs.length];
        for (int i = 0; i < objs.length; i++) {
            groups[i] = ((Group) objs[i]).getId();
        }
        return groups;
    }
}
