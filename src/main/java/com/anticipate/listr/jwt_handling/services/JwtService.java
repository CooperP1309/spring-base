package com.anticipate.listr.jwt_handling.services;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService
{
    // Claim name carrying the per-login CSRF token (see CsrfProtectionFilter).
    // Binding it into the JWT means it rotates with every login and never
    // needs its own server-side storage despite the app being stateless.
    private static final String CSRF_CLAIM = "csrf";

    private final SecretGeneratorService secretGeneratorService;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    public JwtService(SecretGeneratorService secretGeneratorService)
    {
        this.secretGeneratorService = secretGeneratorService;
    }

    public String extractUsername(String token)
    {
        return extractClaim(token, Claims::getSubject);
    }

    /*  FIRSTLY UNDERSTAND WHAT A CLAIM IS:
     *  A claim is a key value pair that's apart of the signed payload
     *  in a jwt token.
     * 
     *  For extractCsrfToken(), this is the exact call order:
     *  
     *  called: extractCsrfToken()
     *  - Because the 2nd param is a lambda, a function object is created (BUT NOT YET RAN!!)
     *      
     *      called: Claims = extractAllClaims()
     *      - In this function, all of the claims in the jwt token are extracted and verified (e.g. expiry,...)
     *      - If a claim in the payload isn't valid, exception is thrown, execution stops here 
     *      
     *      But why have a lambda? Why not just return claims.get(....) from extractClaim()?
     *  
     *      If you look closely at other function in this class, you'll notice that extractClaim()
     *      is used to extract claims other than the csrf claim. By having a lamba as an argument for
     *      this function, we can enforce reusability of extractClaim() for any claim we need.
     */
    public String extractCsrfToken(String token)
    {
        return extractClaim(token, claims -> claims.get(CSRF_CLAIM, String.class));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver)
    {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails)
    {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CSRF_CLAIM, secretGeneratorService.generateSecureSecret());

        return generateToken(claims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails)
    {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    public long getExpirationTime()
    {
        return jwtExpiration;
    }

    private String buildToken(Map<String, Object> extraClaims,
                                UserDetails userDetails,
                                long expiration) 
    {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails)
    {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token)
    {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token)
    {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token)
    {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey()
    {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}