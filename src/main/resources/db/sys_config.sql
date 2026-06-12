CREATE TABLE IF NOT EXISTS sys_config (
    id          BIGINT       NOT NULL PRIMARY KEY,
    config_key  VARCHAR(64)  NOT NULL,
    config_value TEXT,
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      DEFAULT 0,
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
