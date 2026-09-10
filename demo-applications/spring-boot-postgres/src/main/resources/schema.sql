-- Table definition for products catalog
CREATE TABLE IF NOT EXISTS products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50),
    price NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock INT NOT NULL CHECK (stock >= 0)
);

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);

-- Seed data for initial catalog
INSERT INTO products (name, category, price, stock) VALUES
('Quantum Laptop', 'Electronics', 1299.99, 45),
('Ergonomic Mechanical Keyboard', 'Accessories', 149.50, 120),
('Noise Cancelling Headphones', 'Audio', 299.00, 75)
ON CONFLICT DO NOTHING;
