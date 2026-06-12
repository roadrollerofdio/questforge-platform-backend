-- =============================================================
-- 多邻国风格游戏化改造 迁移脚本
-- 包含: sys_user / stage 增列 + 6 张新表 + 种子数据
-- =============================================================

-- 1. sys_user 增列: 昵称 / 宝石余额 / 虚拟形象配置(JSON)
ALTER TABLE sys_user
    ADD COLUMN nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称(默认取 real_name)',
    ADD COLUMN gems          INT          NOT NULL DEFAULT 0 COMMENT '宝石余额',
    ADD COLUMN avatar_config TEXT         COMMENT '虚拟形象配置 JSON: {skin,hair,face,hat,glasses,outfit,background}';

-- 2. stage 增列: 通关奖励宝石(管理员设置)
ALTER TABLE stage
    ADD COLUMN gem_reward INT NOT NULL DEFAULT 10 COMMENT '通关奖励宝石数';

-- 3. 每日任务池
CREATE TABLE IF NOT EXISTS daily_task_pool (
    id           BIGINT       NOT NULL PRIMARY KEY,
    title        VARCHAR(128) NOT NULL COMMENT '任务标题',
    task_type    VARCHAR(32)  NOT NULL COMMENT '任务类型: STAGE_COMPLETE-完成关卡 / STAGE_PERFECT-无错通关 / ASK_AI-询问AI教师',
    target_count INT          NOT NULL DEFAULT 1 COMMENT '目标次数',
    gem_reward   INT          NOT NULL DEFAULT 10 COMMENT '完成奖励宝石',
    enabled      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT      DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日任务池';

-- 4. 每日任务计划(某日选定的 3 个任务)
CREATE TABLE IF NOT EXISTS daily_task_plan (
    id           BIGINT   NOT NULL PRIMARY KEY,
    plan_date    DATE     NOT NULL COMMENT '生效日期',
    task_pool_id BIGINT   NOT NULL COMMENT '任务池ID',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT  DEFAULT 0,
    KEY idx_plan_date (plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日任务计划';

-- 5. 用户每日任务进度
CREATE TABLE IF NOT EXISTS user_daily_task (
    id           BIGINT   NOT NULL PRIMARY KEY,
    user_id      BIGINT   NOT NULL,
    plan_date    DATE     NOT NULL,
    task_pool_id BIGINT   NOT NULL,
    progress     INT      NOT NULL DEFAULT 0 COMMENT '当前进度',
    is_completed TINYINT  NOT NULL DEFAULT 0,
    is_rewarded  TINYINT  NOT NULL DEFAULT 0 COMMENT '宝石是否已发放',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT  DEFAULT 0,
    KEY idx_user_date (user_id, plan_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户每日任务进度';

-- 6. 好友关系/申请
CREATE TABLE IF NOT EXISTS friend_relation (
    id           BIGINT   NOT NULL PRIMARY KEY,
    from_user_id BIGINT   NOT NULL COMMENT '申请发起方',
    to_user_id   BIGINT   NOT NULL COMMENT '申请接收方',
    status       TINYINT  NOT NULL DEFAULT 0 COMMENT '0-待处理 1-已接受 2-已拒绝',
    create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted   TINYINT  DEFAULT 0,
    KEY idx_from_user (from_user_id),
    KEY idx_to_user (to_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系与申请';

-- 7. 宝石小店商品(引用内置 SVG 装扮部件)
CREATE TABLE IF NOT EXISTS shop_item (
    id          BIGINT       NOT NULL PRIMARY KEY,
    name        VARCHAR(64)  NOT NULL COMMENT '商品名称',
    slot        VARCHAR(32)  NOT NULL COMMENT '装扮部位: hat / glasses / outfit / background',
    svg_key     VARCHAR(64)  NOT NULL COMMENT '内置 SVG 部件 key',
    price       INT          NOT NULL DEFAULT 50 COMMENT '宝石价格',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1-上架 0-下架',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT      DEFAULT 0,
    UNIQUE KEY uk_svg_key (svg_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='宝石小店商品';

-- 8. 用户已购装扮
CREATE TABLE IF NOT EXISTS user_item (
    id          BIGINT   NOT NULL PRIMARY KEY,
    user_id     BIGINT   NOT NULL,
    item_id     BIGINT   NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted  TINYINT  DEFAULT 0,
    UNIQUE KEY uk_user_item (user_id, item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户已购装扮';

-- =============================================================
-- 种子数据
-- =============================================================

-- 每日任务池默认任务
INSERT INTO daily_task_pool (id, title, task_type, target_count, gem_reward, enabled) VALUES
(1001, '完成 1 个关卡',        'STAGE_COMPLETE', 1, 10, 1),
(1002, '完成 3 个关卡',        'STAGE_COMPLETE', 3, 30, 1),
(1003, '无错完成 1 个关卡',    'STAGE_PERFECT',  1, 25, 1),
(1004, '向 AI 教师提 1 个问题', 'ASK_AI',         1, 10, 1),
(1005, '向 AI 教师提 3 个问题', 'ASK_AI',         3, 25, 1);

-- 宝石小店默认商品(对应前端内置 SVG 装扮部件库)
INSERT INTO shop_item (id, name, slot, svg_key, price, status) VALUES
(2001, '棒球帽',   'hat',        'hat_cap',     60,  1),
(2002, '国王皇冠', 'hat',        'hat_crown',   200, 1),
(2003, '魔法师帽', 'hat',        'hat_wizard',  120, 1),
(2004, '派对帽',   'hat',        'hat_party',   80,  1),
(2005, '圆框眼镜', 'glasses',    'gl_round',    50,  1),
(2006, '酷炫墨镜', 'glasses',    'gl_sun',      90,  1),
(2007, '星星眼镜', 'glasses',    'gl_star',     110, 1),
(2008, '红色围巾', 'outfit',     'outfit_scarf', 70, 1),
(2009, '绅士领结', 'outfit',     'outfit_bow',   60, 1),
(2010, '超人披风', 'outfit',     'outfit_cape', 150, 1),
(2011, '星空背景', 'background', 'bg_stars',    100, 1),
(2012, '彩虹背景', 'background', 'bg_rainbow',  100, 1),
(2013, '云朵背景', 'background', 'bg_clouds',    80, 1);
