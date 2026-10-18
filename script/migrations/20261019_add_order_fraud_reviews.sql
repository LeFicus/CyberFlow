-- Reviews are local, append-only and bound to the order's exact normalized email.
-- Re-syncing platform orders never overwrites these decisions.
CREATE TABLE IF NOT EXISTS order_fraud_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_group VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    decision VARCHAR(16) NOT NULL,
    reviewer VARCHAR(100) NOT NULL,
    note VARCHAR(500) NOT NULL,
    reviewed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fraud_review_order (user_group, order_id, email, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
