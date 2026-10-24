package com.serviceportal.dto;

import com.serviceportal.model.*;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** Request and response shapes for the REST API. */
public final class Dtos {
    private Dtos() {}

    // ---------- requests ----------
    public record LocationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            String address) {}

    public record LanguagesRequest(@NotEmpty Set<Long> languageIds) {}

    public record TechnicianProfileRequest(
            @Size(max = 2000) String qualification,
            @Min(0) @Max(80) Integer experienceYears) {}

    public record SkillRequest(
            @NotNull Long professionId,
            @NotNull SkillLevel skillLevel,
            @Size(max = 1000) String notes) {}

    public record PreferenceRequest(
            String preferredGender,
            @Positive Double maxDistanceKm,
            @PositiveOrZero BigDecimal budgetMin,
            @PositiveOrZero BigDecimal budgetMax,
            @DecimalMin("0") @DecimalMax("5") Double minRating,
            Set<Long> preferredLanguageIds) {}

    public record NameRequest(@NotBlank String name) {}

    public record ProfessionRequest(
            @NotBlank String name,
            @NotNull @PositiveOrZero BigDecimal minPrice,
            @NotNull @PositiveOrZero BigDecimal maxPrice,
            @Size(max = 2000) String keywords) {}

    public record UpdateDetailsRequest(
            @NotBlank @Size(max = 100) String fullName,
            @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Enter a valid contact number (9-15 digits)") String contactNumber,
            @NotBlank String gender) {}

    public record PayoutRequest(@NotBlank @Size(max = 200) String reference, @Size(max = 1000) String note) {}

    public record RejectRequest(@NotBlank String reason) {}

    public record MatchRequest(
            @NotBlank String description,
            Double latitude,
            Double longitude,
            Double radiusKm) {}

    public record BookingCreateRequest(
            @NotNull Long technicianId,
            @NotNull Long professionId,
            @NotBlank @Size(max = 2000) String description,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double jobLatitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double jobLongitude,
            String jobAddress,
            @NotNull @Future LocalDateTime scheduledAt,
            @NotNull @Positive BigDecimal proposedPrice) {}

    public record OfferRequest(@NotNull @Positive BigDecimal amount) {}

    public record RatingRequest(@Min(1) @Max(5) int score, @Size(max = 1000) String comment) {}

    // ---------- responses ----------
    public record LanguageView(Long id, String name) {
        public static LanguageView of(Language l) { return new LanguageView(l.getId(), l.getName()); }
    }

    public record ProfessionView(Long id, String name, BigDecimal minPrice, BigDecimal maxPrice, String keywords) {
        public static ProfessionView of(Profession p) {
            return new ProfessionView(p.getId(), p.getName(), p.getMinPrice(), p.getMaxPrice(), p.getKeywords());
        }
    }

    public record SkillView(Long id, Long professionId, String profession, SkillLevel skillLevel, String notes,
                            BigDecimal minPrice, BigDecimal maxPrice) {
        public static SkillView of(TechnicianSkill s) {
            return new SkillView(s.getId(), s.getProfession().getId(), s.getProfession().getName(),
                    s.getSkillLevel(), s.getNotes(), s.getProfession().getMinPrice(), s.getProfession().getMaxPrice());
        }
    }

    public record UserView(Long id, String fullName, String email, Role role, VerificationStatus status,
                           String rejectionReason, String contactNumber, String gender, String nic, String address,
                           Double latitude, Double longitude, List<LanguageView> languages,
                           String qualification, Integer experienceYears, Double ratingAverage, Integer ratingCount,
                           List<SkillView> skills) {}

    public record PreferenceView(String preferredGender, Double maxDistanceKm, BigDecimal budgetMin, BigDecimal budgetMax,
                                 Double minRating, List<LanguageView> preferredLanguages) {}

    public record TechnicianResult(Long id, String fullName, String gender, List<LanguageView> languages,
                                   List<SkillView> skills, String qualification, Integer experienceYears,
                                   double ratingAverage, int ratingCount, boolean rated,
                                   Double latitude, Double longitude, double distanceKm) {}

    public record ProfessionMatchView(Long professionId, String profession, int score) {}

    public record MatchResponse(List<ProfessionMatchView> matches, ProfessionMatchView best,
                                List<TechnicianResult> technicians) {}

    public record PartyView(Long id, String fullName, String contactNumber) {}

    public record OfferView(Long id, Role by, String byName, BigDecimal amount, LocalDateTime at) {}

    public record BookingView(Long id, BookingStatus status, PartyView customer, PartyView technician,
                              ProfessionView profession, String description,
                              Double jobLatitude, Double jobLongitude, String jobAddress, LocalDateTime scheduledAt,
                              BigDecimal currentOffer, Role currentOfferBy, BigDecimal agreedPrice,
                              BigDecimal serviceCharge, BigDecimal technicianPayout,
                              List<OfferView> offers, Integer ratingScore, LocalDateTime createdAt) {}

    public record PaymentView(Long id, Long bookingId, Long payerId, String payerName, PaymentType type,
                              PaymentMethod method, BigDecimal amount, PaymentStatus status,
                              boolean hasProof, String proofFileName, String note,
                              LocalDateTime createdAt, LocalDateTime reviewedAt) {}

    public record RatingView(Long id, Long bookingId, int score, String comment, LocalDateTime createdAt) {}

    public record RouteStep(String instruction, String road, double distanceMeters, double durationSeconds) {}

    public record RouteView(String mode, String label, String note, Double distanceKm, Double durationMinutes,
                            List<RouteStep> steps, List<double[]> geometry, String error) {}

    public record RoutesResponse(Long bookingId, double fromLatitude, double fromLongitude,
                                 double toLatitude, double toLongitude, String toAddress,
                                 List<RouteView> routes, String transitNote) {}

    public record StatusCount(BookingStatus status, long count) {}

    public record DashboardView(List<StatusCount> bookingsByStatus, BigDecimal totalEarned, BigDecimal pendingPayout,
                                BigDecimal serviceChargePaid, Double ratingAverage, int ratingCount,
                                List<BookingView> upcomingJobs, List<PartyView> customers) {}

    // ---------- extra features ----------
    public record DocumentView(Long id, String label, String fileName, String contentType, long size, LocalDateTime uploadedAt) {}

    public record PendingPayoutView(Long bookingId, Long technicianId, String technicianName, BigDecimal amount, Long paymentId) {}

    public record PayoutView(Long id, Long bookingId, Long technicianId, String technicianName, BigDecimal amount,
                             String reference, String note, LocalDateTime paidAt) {}

    public record PlaceView(String label, double latitude, double longitude) {}

    public record ReviewView(int score, String comment, String customerName, LocalDateTime createdAt) {}

    /** What anyone signed in may see about a verified technician: no contact number, NIC or address. */
    public record TechnicianPublicView(Long id, String fullName, String gender, List<LanguageView> languages,
                                       List<SkillView> skills, String qualification, Integer experienceYears,
                                       double ratingAverage, int ratingCount, List<ReviewView> reviews) {}
}
