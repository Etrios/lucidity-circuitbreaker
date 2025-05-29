-- schema.sql

CREATE TABLE IF NOT EXISTS USERS (
    id      VARCHAR(60)  DEFAULT RANDOM_UUID() PRIMARY KEY,
    name    VARCHAR(60)  NOT NULL,
    email   VARCHAR(60)  NOT NULL
    );

TRUNCATE TABLE USERS;
INSERT INTO USERS (id, name, email) VALUES
('ed341a08-7748-4e29-85b3-3d1b31f343cf', 'Alice Wonderland', 'alice.w@example.com'),
('3d198fcf-8368-4708-a885-b240b6aeee7b', 'Bob The Builder', 'bob.t.b@example.com'),
('4a149f82-3b18-48ce-91a3-81ca732bcc4f', 'Charlie Chaplin', 'charlie.c@example.com');