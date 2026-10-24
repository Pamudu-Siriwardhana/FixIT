package com.serviceportal.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Gives every page the signed-in role and the path of its home page (used by the navigation bar). */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("currentRole")
    public String currentRole() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a instanceof AnonymousAuthenticationToken || !a.isAuthenticated()) return null;
        for (var g : a.getAuthorities()) {
            if (g.getAuthority().startsWith("ROLE_")) return g.getAuthority().substring(5);
        }
        return null;
    }

    @ModelAttribute("homePath")
    public String homePath() {
        String role = currentRole();
        if ("TECHNICIAN".equals(role)) return "/technician/home";
        if ("ADMIN".equals(role)) return "/admin/home";
        if ("CUSTOMER".equals(role)) return "/customer/home";
        return "/login";
    }
}
