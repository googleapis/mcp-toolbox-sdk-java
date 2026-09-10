-- Table definition for products catalog
CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    price NUMERIC(10,2) NOT NULL,
    stock INT NOT NULL
);

-- Seed data for initial catalog
INSERT INTO products (name, category, price, stock) VALUES
('Quantum Laptop', 'Electronics', 1299.99, 45),
('Ergonomic Mechanical Keyboard', 'Accessories', 149.50, 120),
('Noise Cancelling Headphones', 'Audio', 299.00, 75)
ON CONFLICT DO NOTHING;
