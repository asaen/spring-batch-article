CREATE TABLE IF NOT EXISTS customer_report (
    report_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    customer_name VARCHAR(120) NOT NULL,
    birthday DATE NOT NULL,
    transactions INTEGER NOT NULL,
    PRIMARY KEY (report_date, customer_id)
);
