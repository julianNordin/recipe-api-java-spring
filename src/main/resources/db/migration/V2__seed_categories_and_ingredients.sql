-- Reference data. Categories and ingredients are shared rows that recipes attach themselves to,
-- so the API never creates a category and only creates an ingredient it has not seen before.
-- Seeding the common ones here means a fresh database is immediately usable.
--
-- ON CONFLICT DO NOTHING throughout. Flyway will not re-run this migration - the schema history
-- table sees to that - but a repair or a manual replay should not fail on data that already
-- exists. Note the conflict target for ingredients is lower(name), matching the functional
-- unique index rather than the column.

INSERT INTO categories (name) VALUES
    ('Breakfast'),
    ('Lunch'),
    ('Dinner'),
    ('Dessert'),
    ('Snack'),
    ('Side'),
    ('Vegetarian'),
    ('Vegan'),
    ('Gluten-Free'),
    ('Baking')
ON CONFLICT (name) DO NOTHING;

INSERT INTO ingredients (name) VALUES
    ('Flour'),
    ('Sugar'),
    ('Brown Sugar'),
    ('Salt'),
    ('Black Pepper'),
    ('Butter'),
    ('Milk'),
    ('Egg'),
    ('Olive Oil'),
    ('Garlic'),
    ('Onion'),
    ('Tomato'),
    ('Potato'),
    ('Carrot'),
    ('Chicken Breast'),
    ('Rice'),
    ('Pasta'),
    ('Baking Powder'),
    ('Vanilla Extract'),
    ('Cheddar Cheese'),
    ('Parmesan'),
    ('Lemon'),
    ('Basil'),
    ('Honey')
ON CONFLICT (lower(name)) DO NOTHING;
