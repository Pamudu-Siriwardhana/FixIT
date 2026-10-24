package com.serviceportal.config;

import com.serviceportal.model.*;
import com.serviceportal.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Creates the admin account, the starting languages and professions (with LKR price ranges) when missing. */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final LanguageRepository languages;
    private final ProfessionRepository professions;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public DataSeeder(UserRepository users, LanguageRepository languages, ProfessionRepository professions,
                      PasswordEncoder encoder,
                      @Value("${app.admin.email}") String adminEmail,
                      @Value("${app.admin.password}") String adminPassword,
                      @Value("${app.admin.name:Platform Admin}") String adminName) {
        this.users = users;
        this.languages = languages;
        this.professions = professions;
        this.encoder = encoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    public void run(String... args) {
        for (String name : List.of("Sinhala", "Tamil", "English")) {
            if (!languages.existsByNameIgnoreCase(name)) languages.save(new Language(name));
        }
        seed("Electrician", 1500, 25000, "electric,electrical,wiring,wire,socket,switch,circuit,breaker,fuse,power cut,short circuit,light,bulb,fan,voltage,meter");
        seed("Plumber", 1500, 20000, "plumbing,pipe,leak,leaking,tap,faucet,drain,toilet,water,tank,pump,blocked,sink,geyser");
        seed("Carpenter", 2000, 40000, "wood,wooden,door,window,furniture,cabinet,table,wardrobe,shelf,roof,cupboard,hinge");
        seed("Mason", 3000, 60000, "wall,cement,brick,concrete,plaster,tile,tiles,floor,construction,crack");
        seed("Painter", 2500, 50000, "paint,painting,colour,color,whitewash,coating");
        seed("AC Technician", 2500, 25000, "ac,air conditioner,air conditioning,cooling,gas refill,compressor,split unit");
        seed("Appliance Repair", 1500, 20000, "washing machine,refrigerator,fridge,oven,microwave,tv,television,freezer,appliance,mixer,rice cooker");
        seed("Mechanic", 2000, 60000, "car,vehicle,engine,brake,brakes,motorbike,bike,tyre,tire,battery,gearbox,oil change");

        if (!users.existsByEmail(adminEmail)) {
            UserAccount a = new UserAccount();
            a.setFullName(adminName);
            a.setEmail(adminEmail);
            a.setPassword(encoder.encode(adminPassword));
            a.setContactNumber("0000000000");
            a.setGender("Other");
            a.setNic("ADMIN-" + adminEmail);
            a.setAddress("Platform");
            a.setRole(Role.ADMIN);
            a.setStatus(VerificationStatus.VERIFIED);
            users.save(a);
            log.info("Seeded admin account {}", adminEmail);
        }
    }

    private void seed(String name, int min, int max, String keywords) {
        if (!professions.existsByNameIgnoreCase(name)) {
            professions.save(new Profession(name, BigDecimal.valueOf(min), BigDecimal.valueOf(max), keywords));
        }
    }
}
