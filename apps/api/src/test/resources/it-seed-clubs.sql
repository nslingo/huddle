-- Seed data for ClubControllerIT. Runs after Flyway has applied V1 against the Testcontainers DB.
-- Covers: active/inactive filtering, activity_score ordering (incl. NULL), blurb coalesce+truncate,
-- interest batch-fetch capping/ordering, and — for the detail endpoint — jsonb contacts, club_links
-- rows, and the Instagram link derived from instagram_handle.

-- @Sql runs this before every test method against the per-class container, so reset first to keep
-- the seed idempotent (a second test method would otherwise hit a duplicate-slug unique violation).
-- CASCADE also clears club_interests and club_links (FKs to clubs); RESTART IDENTITY keeps ids
-- deterministic.
TRUNCATE clubs, interests RESTART IDENTITY CASCADE;

INSERT INTO interests (slug, name) VALUES
    ('arts', 'Arts'),
    ('games', 'Games'),
    ('music', 'Music'),
    ('service', 'Service'),
    ('tech', 'Technology');

-- public_id is normally DB-generated; the detail tests address clubs by it, so pin it here.
INSERT INTO clubs (public_id, slug, status, name, description, mission, campusgroups_id, activity_score,
                   goals, club_type, membership_type, instagram_handle, follower_count, logo_url, contacts) VALUES
    ('11111111-1111-4111-8111-111111111111', 'chess-club', 'active', 'Chess Club',
     'The Cornell Chess Club meets weekly to play casual and competitive chess, run tournaments, teach openings and endgames to beginners, and welcome players of every skill level from across campus.',
     'Grow chess at Cornell', 'cg-chess', 90,
     'Win the Ivy tournament', 'Games', 'Open', 'cornellchess', 1200, 'https://cdn.example.com/chess.png',
     '[{"type":"email","value":"chess@cornell.edu"},{"type":"phone","value":"6075551234"}]'::jsonb),
    ('22222222-2222-4222-8222-222222222222', 'art-society', 'active', 'Art Society',
     NULL, 'Make art together', 'cg-art', 50,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    ('33333333-3333-4333-8333-333333333333', 'no-score-club', 'active', 'No Score Club',
     'Just vibes on campus.', NULL, 'cg-noscore', NULL,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL),
    ('44444444-4444-4444-8444-444444444444', 'inactive-club', 'inactive', 'Inactive Club',
     'Should not appear in the feed.', NULL, 'cg-inactive', 99,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL);

-- Chess Club gets 5 interests; the feed caps cards at 3 (ordered by interest name), detail shows all.
INSERT INTO club_interests (club_id, interest_id, source)
SELECT c.id, i.id, 'manual'
FROM clubs c
CROSS JOIN interests i
WHERE c.slug = 'chess-club'
  AND i.slug IN ('arts', 'games', 'music', 'service', 'tech');

-- The stored Instagram URL is deliberately messy (tracking param, trailing slash): the detail
-- endpoint replaces it with a clean URL derived from instagram_handle. The website row passes
-- through untouched. Art Society has an Instagram row but no handle, so its stored URL is kept.
INSERT INTO club_links (club_id, type, url)
SELECT c.id, 'instagram', 'https://www.instagram.com/CornellChess/?igshid=abc123'
FROM clubs c WHERE c.slug = 'chess-club';

INSERT INTO club_links (club_id, type, url)
SELECT c.id, 'website', 'https://chess.cornell.edu'
FROM clubs c WHERE c.slug = 'chess-club';

INSERT INTO club_links (club_id, type, url)
SELECT c.id, 'instagram', 'https://www.instagram.com/artsociety/'
FROM clubs c WHERE c.slug = 'art-society';