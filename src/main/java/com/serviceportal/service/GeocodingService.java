package com.serviceportal.service;

import com.serviceportal.dto.Dtos.PlaceView;

import java.util.List;

/** Turns a typed address or place name into map positions. */
public interface GeocodingService {
    List<PlaceView> search(String text);
}
