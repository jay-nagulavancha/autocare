-- Vehicle Maintenance Service — idempotent schema bootstrap
-- All tables use CREATE TABLE IF NOT EXISTS; seed data uses INSERT IGNORE

CREATE DATABASE IF NOT EXISTS maintenance_db;
USE maintenance_db;

CREATE TABLE IF NOT EXISTS vehicles (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    vin            VARCHAR(17) NOT NULL UNIQUE,
    make           VARCHAR(100),
    model          VARCHAR(100),
    vehicle_year   INT,
    owner_username VARCHAR(100),
    deleted        TINYINT(1) NOT NULL DEFAULT 0,
    created_at     DATETIME
);

CREATE TABLE IF NOT EXISTS technicians (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(200) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS bays (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name   VARCHAR(100) NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS work_orders (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT NOT NULL,
    technician_id  BIGINT,
    bay_id         BIGINT,
    status         VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    description    TEXT,
    created_at     DATETIME,
    CONSTRAINT fk_wo_vehicle     FOREIGN KEY (vehicle_id)    REFERENCES vehicles(id)    ON DELETE RESTRICT,
    CONSTRAINT fk_wo_technician  FOREIGN KEY (technician_id) REFERENCES technicians(id) ON DELETE RESTRICT,
    CONSTRAINT fk_wo_bay         FOREIGN KEY (bay_id)        REFERENCES bays(id)        ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS work_order_status_history (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id    BIGINT NOT NULL,
    previous_status  VARCHAR(20),
    new_status       VARCHAR(20) NOT NULL,
    changed_by       VARCHAR(100),
    changed_at       DATETIME NOT NULL,
    CONSTRAINT fk_wosh_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS part_lines (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    part_name     VARCHAR(200) NOT NULL,
    quantity      INT NOT NULL,
    unit_cost     DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_pl_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS labor_lines (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    description   VARCHAR(500) NOT NULL,
    hours         DECIMAL(6, 2) NOT NULL,
    rate          DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_ll_work_order FOREIGN KEY (work_order_id) REFERENCES work_orders(id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS service_schedules (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id   BIGINT NOT NULL,
    bay_id       BIGINT,
    scheduled_at DATETIME NOT NULL,
    service_type VARCHAR(200) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    CONSTRAINT fk_ss_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_ss_bay     FOREIGN KEY (bay_id)     REFERENCES bays(id)     ON DELETE RESTRICT
);

-- Seed data (idempotent)
INSERT IGNORE INTO bays (name, active) VALUES ('Bay 1', 1), ('Bay 2', 1);
INSERT IGNORE INTO technicians (name, active) VALUES ('Admin Technician', 1);
