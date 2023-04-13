CREATE TABLE IF NOT EXISTS event_tag (
    event_id BIGINT UNSIGNED NOT NULL,
    tag VARCHAR(255) NOT NULL,
    PRIMARY KEY(event_id, tag),
    FOREIGN KEY (event_id)
        REFERENCES event_journal(ordering)
        ON DELETE CASCADE
    )