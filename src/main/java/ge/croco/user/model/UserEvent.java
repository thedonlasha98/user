package ge.croco.user.model;

import ge.croco.user.enums.EventType;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class UserEvent extends UserDetails {
    private EventType eventType;
    private Instant timestamp;

    public UserEvent(EventType eventType, Instant timestamp, UserDetails user) {
        super(user.getId(), user.getUsername(), user.getEmail(), user.getRoles());
        this.eventType = eventType;
        this.timestamp = timestamp;
    }
}
