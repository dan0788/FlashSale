INSERT INTO products (id, name, description, original_price) 
VALUES (1, 'PlayStation 5 Slim 1TB', 'Consola de última generación Sony', 499.99);

INSERT INTO flash_sale_events (id, name, start_time, end_time, status)
VALUES (1, 'Gran Venta Flash de Medianoche', CURRENT_TIMESTAMP - INTERVAL '1 hour', CURRENT_TIMESTAMP + INTERVAL '24 hours', 'ACTIVE');

INSERT INTO flash_sale_products (id, event_id, product_id, flash_price, total_stock, available_stock, version)
VALUES (1, 1, 1, 99.99, 10, 10, 0);

SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));
SELECT setval('flash_sale_events_id_seq', (SELECT MAX(id) FROM flash_sale_events));
SELECT setval('flash_sale_products_id_seq', (SELECT MAX(id) FROM flash_sale_products));
