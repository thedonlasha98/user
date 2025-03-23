package ge.croco.user.security;

import ge.croco.user.domain.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtTokenUtil {

    @Value("${jwt.secret.key}")
    private String secretKey;

    @Value("${jwt.expiration-ms}")
    private int expirationMs;

    public String generateToken(CustomUserDetails userDetails) {


        return Jwts.builder()
                .claim("authorities", extractRoles(userDetails.getAuthorities()))
                .claim("userId", userDetails.getId())
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey(secretKey), SignatureAlgorithm.HS512)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    public static Claims extractAllClaims(String token, String secretKey) {
        return extractAllClaims(token, getSigningKey(secretKey));
    }

    public Claims extractAllClaims(String token) {
        return extractAllClaims(token, getSigningKey(secretKey));
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject(); // Extract the "sub" claim (username)
    }

    public Long extractUserId(String token) {
        return extractAllClaims(token).get("userId", Long.class); // Extract the "userId" claim
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey(secretKey)).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false; // Token is invalid
        }
    }

    private static Key getSigningKey(String secretKey) {
        return Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secretKey));
    }

    private static Claims extractAllClaims(String token, Key signingKey) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey) // Use the secret key to verify the token
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Set<String> extractRoles(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}