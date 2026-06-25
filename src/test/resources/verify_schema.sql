CREATE TABLE IF NOT EXISTS apparels (
    id INTEGER PRIMARY KEY,
    content TEXT,
    uri TEXT,
    category TEXT,
    sub_category TEXT,
    color TEXT,
    gender TEXT
);

INSERT INTO apparels (content, uri, category, sub_category, color, gender) VALUES
('Red T-Shirt', 'http://example.com/red-tshirt', 'apparel', 'tshirt', 'red', 'unisex'),
('Blue Jeans', 'http://example.com/blue-jeans', 'apparel', 'jeans', 'blue', 'unisex');

CREATE TABLE IF NOT EXISTS toys (
    id INTEGER PRIMARY KEY,
    name TEXT,
    description TEXT,
    price REAL
);

INSERT INTO toys (name, description, price) VALUES
('Barbie', 'barbie doll', 19.99),
('Teddy Bear', 'soft toy teddy bear', 14.99),
('Lego Set', 'construction blocks lego', 49.99);
