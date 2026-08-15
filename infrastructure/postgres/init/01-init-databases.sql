-- Initialize all isolated microservice databases
CREATE DATABASE commercecore_auth;
CREATE DATABASE commercecore_user;
CREATE DATABASE commercecore_inventory;
CREATE DATABASE commercecore_order;
CREATE DATABASE commercecore_payment;
CREATE DATABASE commercecore_notification;
CREATE DATABASE commercecore_review;

-- Grant privileges to default postgres user
GRANT ALL PRIVILEGES ON DATABASE commercecore_auth TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_user TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_inventory TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_order TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_payment TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_notification TO postgres;
GRANT ALL PRIVILEGES ON DATABASE commercecore_review TO postgres;
