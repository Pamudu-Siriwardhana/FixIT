package com.serviceportal.service;

import com.serviceportal.dto.Dtos.RouteView;

import java.util.List;

/** Route lookup between two GPS points for each supported way of travelling. */
public interface RoutingService {
    String TRANSIT_NOTE = "Bus and train routes, fares and timetables are not available.";

    List<RouteView> routes(double fromLat, double fromLng, double toLat, double toLng);
}
