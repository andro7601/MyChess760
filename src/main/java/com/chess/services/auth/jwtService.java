package  com.chess.services.auth;

import com.chess.models.PlayerModel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class jwtService {

    @Value("${jwt.secret}")
    private String SECRET;

    @Value("${jwt.expiration}")
    private long EXPIRATION_TIME;

    public String generateToken(PlayerModel player) {
        return Jwts.builder()
                .subject(player.getId().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() +EXPIRATION_TIME)) // 24 hours
                .signWith(getKey())
                .compact();
    }

    public Long extractId(String token) {
        String subject = Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        return Long.parseLong(subject);
    }

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes());
    }
}