-- Seed data for ClubControllerIT. Runs after Flyway has applied V1 against the Testcontainers DB.
-- Covers: active/inactive filtering, activity_score ordering (incl. NULL), blurb coalesce+truncate,
-- and interest batch-fetch capping/ordering.

-- @Sql runs this before every test method against the per-class container, so reset first to keep
-- the seed idempotent (a second test method would otherwise hit a duplicate-slug unique violation).
-- CASCADE also clears club_interests (FK to clubs); RESTART IDENTITY keeps ids deterministic.
TRUNCATE clubs, interests RESTART IDENTITY CASCADE;

INSERT INTO interests (slug, name) VALUES
    ('arts', 'Arts'),
    ('games', 'Games'),
    ('music', 'Music'),
    ('service', 'Service'),
    ('tech', 'Technology');

INSERT INTO clubs (slug, status, name, description, mission, campusgroups_id, activity_score) VALUES
    ('chess-club', 'active', 'Chess Club',
     'The Cornell Chess Club meets weekly to play casual and competitive chess, run tournaments, teach openings and endgames to beginners, and welcome players of every skill level from across campus.',
     NULL, 'cg-chess', 90),
    ('art-society', 'active', 'Art Society', NULL, 'Make art together', 'cg-art', 50),
    ('no-score-club', 'active', 'No Score Club', 'Just vibes on campus.', NULL, 'cg-noscore', NULL),
    ('inactive-club', 'inactive', 'Inactive Club', 'Should not appear in the feed.', NULL, 'cg-inactive', 99);

-- Chess Club gets 5 interests; the feed caps cards at 3 (ordered by interest name).
INSERT INTO club_interests (club_id, interest_id, source)
SELECT c.id, i.id, 'manual'
FROM clubs c
CROSS JOIN interests i
WHERE c.slug = 'chess-club'
  AND i.slug IN ('arts', 'games', 'music', 'service', 'tech');