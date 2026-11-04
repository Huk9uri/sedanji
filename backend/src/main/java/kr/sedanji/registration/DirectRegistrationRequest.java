package kr.sedanji.registration;

import jakarta.validation.constraints.*;
import kr.sedanji.mention.MentionType;

public record
DirectRegistrationRequest(
    @NotBlank @Size(max=100) String placeSelectionToken,
    @NotNull @Positive @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictChefIdDeserializer.class) Long chefId,
    @NotBlank @Pattern(regexp="[A-Za-z0-9_-]{11}") String videoId,
    @NotNull @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictMentionTypeDeserializer.class) MentionType mentionType,
    @Min(0) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictTimestampDeserializer.class) Integer timestampSeconds,
    @Size(max=2000) String verificationNote) {
    public void validate() {
        if (placeSelectionToken == null || placeSelectionToken.isBlank() || placeSelectionToken.length() > 100
            || chefId == null || chefId <= 0 || videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")
            || mentionType == null || (timestampSeconds != null && timestampSeconds < 0)
            || (verificationNote != null && verificationNote.length() > 2000)) throw DirectRegistrationException.invalid();
    }
}
