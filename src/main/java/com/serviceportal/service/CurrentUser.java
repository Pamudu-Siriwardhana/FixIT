package com.serviceportal.service;

import com.serviceportal.model.Role;
import com.serviceportal.model.UserAccount;
import com.serviceportal.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public UserAccount of(Principal principal) {
        return users.findByEmail(principal.getName())
                .orElseThrow(() -> ApiException.notFound("Account not found"));
    }

    public UserAccount require(Principal principal, Role role) {
        UserAccount u = of(principal);
        if (u.getRole() != role) {
            throw ApiException.forbidden("This action is for " + role + " accounts");
        }
        return u;
    }

    /** Unverified users may edit their profile but cannot book, bid or be listed. */
    public static void requireVerified(UserAccount u) {
        if (!u.isVerified()) {
            throw ApiException.forbidden("Your profile is " + u.getStatus() + ". It must be VERIFIED by an admin before you can do this.");
        }
    }
}
