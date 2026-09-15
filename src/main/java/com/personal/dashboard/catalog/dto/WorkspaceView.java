package com.personal.dashboard.catalog.dto;

import java.util.List;

/** Safe initial server-rendered workspace state and refresh response. */
public record WorkspaceView(
    List<DeviceView> devices,
    List<ApplicationView> applications,
    List<ClipView> clips,
    List<BookmarkView> bookmarks,
    List<ActivityView> activity,
    Preferences preferences,
    BrowserSettings browserSettings,
    List<TabView> tabs) {}
