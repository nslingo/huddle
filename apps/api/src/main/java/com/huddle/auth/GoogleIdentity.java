package com.huddle.auth;

/**
 * The verified subset of a Google ID token that we persist. Only produced by
 * {@link GoogleIdTokenVerifier} after signature, issuer, audience, expiry, email-verification and
 * domain checks have all passed — so holding one of these means the identity is trustworthy.
 *
 * @param sub     Google's stable, immutable user id — the upsert key. Deliberately used instead of
 *                email, which can be reassigned by the Workspace admin.
 * @param email   already lowercased, and guaranteed to be in the allowed domain.
 * @param name    display name; may be null.
 * @param picture avatar URL; may be null.
 */
public record GoogleIdentity(String sub, String email, String name, String picture) {
}